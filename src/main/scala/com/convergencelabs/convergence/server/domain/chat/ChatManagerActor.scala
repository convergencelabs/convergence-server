/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.chat

import java.time.Instant

import akka.actor.{Actor, ActorLogging, Props, Status, actorRef2Scala}
import com.convergencelabs.convergence.server.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.datastore.domain.schema.ChatClass
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.ChatAlreadyExistsException
import com.convergencelabs.convergence.server.domain.chat.ChatStateManager.ChatPermissions

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class ChatManagerActor private[domain](provider: DomainPersistenceProvider) extends Actor with ActorLogging {

  import ChatManagerActor._

  private[this] val chatStore: ChatStore = provider.chatStore
  private[this] val permissionsStore: PermissionsStore = provider.permissionsStore

  def receive: Receive = {
    case message: CreateChatRequest =>
      onCreateChannel(message)
    case message: GetChatsRequest =>
      onGetChats(message)
    case message: GetJoinedChatsRequest =>
      onGetJoinedChats(message)
    case message: GetDirectChatsRequest =>
      onGetDirect(message)
    case message: ChatsExistsRequest =>
      onExists(message)
    case message: ChatsSearchRequest =>
      onSearchChats(message)
    case message: GetChatInfo =>
      onGetChat(message)
  }

  private[this] def onCreateChannel(message: CreateChatRequest): Unit = {
    val CreateChatRequest(channelId, createdBy, chatType, membership, name, topic, members) = message
    hasPermission(createdBy, ChatPermissions.CreateChannel).map { _ =>
      (for {
        id <- createChannel(channelId, chatType, membership, name, topic, members, createdBy)
        forRecord <- chatStore.getChatRid(id)
        _ <- permissionsStore.addUserPermissions(ChatStateManager.AllChatPermissions, createdBy, Some(forRecord))
      } yield {
        sender ! CreateChatResponse(id)
      }) recover {
        case _: DuplicateValueException =>
          // FIXME how to deal with this? The channel id should only conflict if it was
          //   defined by the user.
          val cId = channelId.get
          sender ! Status.Failure(ChatAlreadyExistsException(cId))
        case NonFatal(cause) =>
          sender ! Status.Failure(cause)
      }
    }
  }

  private[this] def onSearchChats(message: ChatsSearchRequest): Unit = {
    val ChatsSearchRequest(searchTerm, searchFields, chatType, membership, offset, limit) = message

    chatStore.searchChats(searchTerm, searchFields, chatType, membership, offset, limit).map { info =>
      sender ! info
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def onGetChat(message: GetChatInfo): Unit = {
    val GetChatInfo(chatId) = message
    chatStore.getChatInfo(chatId).map { info =>
      sender ! info
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def onGetChats(message: GetChatsRequest): Unit = {
    val GetChatsRequest(sk, ids) = message
    Try(ids.map(chatStore.findChatInfo(_).get)).flatMap { chatInfos =>
      Try {
        chatInfos.collect {
          case Some(chatInfo) if chatInfo.membership == ChatMembership.Public || hasPermission(sk, chatInfo.id, ChatPermissions.JoinChannel).get =>
            chatInfo
        }
      }
    } match {
      case Success(chatInfos) =>
        sender ! GetChatsResponse(chatInfos)
      case Failure(cause) =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def onExists(message: ChatsExistsRequest): Unit = {
    val ChatsExistsRequest(sk, ids) = message
    Try(ids.map(chatStore.findChatInfo(_).get)).flatMap { chatInfos =>
      Try {
        chatInfos.map {
          case Some(chatInfo) =>
            chatInfo.membership == ChatMembership.Public || hasPermission(sk, chatInfo.id, ChatPermissions.JoinChannel).get
          case None =>
            false
        }
      }
    } match {
      case Success(chatInfos) =>
        sender ! ChatsExistsResponse(chatInfos)
      case Failure(cause) =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def onGetDirect(message: GetDirectChatsRequest): Unit = {
    val GetDirectChatsRequest(userId, userIdList) = message
    // FIXME support multiple channel requests.

    // The channel must contain the user who is looking it up and any other users
    val userIds = userIdList.head + userId

    chatStore.getDirectChatInfoByUsers(userIds) flatMap {
      case Some(c) =>
        // The channel exists, just return it.
        Success(c)
      case None =>
        // Does not exists, so create it.
        createChannel(None, ChatType.Direct, ChatMembership.Private, None, None, userIds, userId) flatMap { channelId =>
          // Create was successful, now let's just get the channel.
          chatStore.getChatInfo(channelId)
        } recoverWith {
          case DuplicateValueException(ChatClass.Fields.Members, _, _) =>
            // The channel already exists based on the members, this must have been a race condition.
            // So just try to get it again
            chatStore.getDirectChatInfoByUsers(userIds) flatMap {
              case Some(c) =>
                // Yup it's there.
                Success(c)
              case None =>
                // We are now in a bizaro world where we are told the channel exists, but can
                // not look it up.
                Failure(new IllegalStateException("Can not create direct channel, due to an unexpected error"))
            }
        }
    } map { channel =>
      sender ! GetDirectChatsResponse(Set(channel))
    } recover {
      case cause: Throwable =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def onGetJoinedChats(message: GetJoinedChatsRequest): Unit = {
    val GetJoinedChatsRequest(userId) = message
    this.chatStore.getJoinedChannels(userId) map { channels =>
      sender ! GetJoinedChatsResponse(channels)
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def createChannel(chatId: Option[String],
                                  ct: ChatType.Value,
                                  membership: ChatMembership.Value,
                                  name: Option[String],
                                  topic: Option[String],
                                  members: Set[DomainUserId],
                                  createdBy: DomainUserId): Try[String] = {

    this.chatStore.createChat(
      chatId, ct, Instant.now(), membership, name.getOrElse(""), topic.getOrElse(""), Some(members), createdBy)
  }

  private[this] def hasPermission(userId: DomainUserId, permission: String): Try[Boolean] = {
    if (userId.isConvergence) {
      Success(true)
    } else {
      permissionsStore.hasPermission(userId, permission)
    }
  }

  private[this] def hasPermission(userId: DomainUserId, chatId: String, permission: String): Try[Boolean] = {
    if (userId.isConvergence) {
      Success(true)
    } else {
      for {
        rid <- chatStore.getChatRid(chatId)
        permission <- permissionsStore.hasPermission(userId, rid, permission)
      } yield permission
    }
  }
}

object ChatManagerActor {

  val RelativePath = "chatManagerActor"

  def props(provider: DomainPersistenceProvider): Props = Props(new ChatManagerActor(provider))

  trait ChatStoreRequest

  case class ChatsSearchRequest(searchTerm: Option[String],
                                searchFields: Option[Set[String]],
                                chatType: Option[Set[ChatType.Value]],
                                membership: Option[ChatMembership.Value],
                                offset: Option[Long],
                                limit: Option[Long]) extends ChatStoreRequest

  case class GetChatInfo(chatId: String) extends ChatStoreRequest

  case class CreateChatRequest(chatId: Option[String],
                                createdBy: DomainUserId,
                                chatType: ChatType.Value,
                                membership: ChatMembership.Value,
                                name: Option[String],
                                topic: Option[String],
                                members: Set[DomainUserId]) extends ChatStoreRequest

  case class CreateChatResponse(channelId: String)

  case class GetChatsRequest(userId: DomainUserId, ids: Set[String])

  case class GetChatsResponse(channels: Set[ChatInfo])

  case class ChatsExistsRequest(userId: DomainUserId, ids: List[String])

  case class ChatsExistsResponse(chatIds: List[Boolean])

  case class GetJoinedChatsRequest(userId: DomainUserId)

  case class GetJoinedChatsResponse(channels: Set[ChatInfo])

  case class GetDirectChatsRequest(userId: DomainUserId, userLists: Set[Set[DomainUserId]])

  case class GetDirectChatsResponse(channels: Set[ChatInfo])

  val DefaultPermissions = List()
}