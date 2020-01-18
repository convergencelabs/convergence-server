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
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.datastore.domain.schema.ChatClass
import com.convergencelabs.convergence.server.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.ChatAlreadyExistsException
import com.convergencelabs.convergence.server.domain.chat.ChatStateManager.ChatPermissions
import com.convergencelabs.convergence.server.domain.{DomainUserId, UnauthorizedException}

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class ChatManagerActor private[domain](provider: DomainPersistenceProvider) extends Actor with ActorLogging {

  import ChatManagerActor._

  private[this] val chatStore: ChatStore = provider.chatStore
  private[this] val permissionsStore: PermissionsStore = provider.permissionsStore

  def receive: Receive = {
    case message: CreateChatRequest =>
      onCreateChannel(message)
    case message: GetChannelsRequest =>
      onGetChannels(message)
    case message: GetJoinedChannelsRequest =>
      onGetJoinedChannels(message)
    case message: GetDirectChannelsRequest =>
      onGetDirect(message)
    case message: ChannelsExistsRequest =>
      onExists(message)
    case message: FindChatInfo =>
      onGetChats(message)
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

  private[this] def onGetChats(message: FindChatInfo): Unit = {
    val FindChatInfo(filter, offset, limit) = message
    chatStore.findChats(None, filter, offset, limit).map { info =>
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

  private[this] def onGetChannels(message: GetChannelsRequest): Unit = {
    val GetChannelsRequest(sk, ids) = message
    // TODO support multiple.
    val id = ids.head
    chatStore.getChatInfo(id).map { info =>
      if (info.membership == ChatMembership.Private) {
        sender ! GetChannelsResponse(List(info))
      } else {
        hasPermission(sk, ChatPermissions.JoinChannel).map { _ =>
          sender ! GetChannelsResponse(List(info))
        }
      } recover {
        case _: UnauthorizedException =>
          sender ! ChannelsExistsResponse(List(false))
        case cause: Exception =>
          sender ! Status.Failure(cause)
      }
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def onExists(message: ChannelsExistsRequest): Unit = {
    val ChannelsExistsRequest(sk, ids) = message
    // TODO support multiple.
    // FIXME this should be an option or something.
    val id = ids.head
    chatStore.getChatInfo(id).map { info =>
      if (info.membership == ChatMembership.Private) {
        sender ! ChannelsExistsResponse(List(true))
      } else {
        hasPermission(sk, ChatPermissions.JoinChannel).map { _ =>
          sender ! ChannelsExistsResponse(List(true))
        } recover {
          case _: UnauthorizedException =>
            sender ! ChannelsExistsResponse(List(false))
          case cause: Exception =>
            sender ! Status.Failure(cause)
        }
      }
    } recover {
      case _: EntityNotFoundException =>
        sender ! ChannelsExistsResponse(List(false))
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def onGetDirect(message: GetDirectChannelsRequest): Unit = {
    val GetDirectChannelsRequest(userId, userIdList) = message
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
      sender ! GetDirectChannelsResponse(List(channel))
    } recover {
      case cause: Throwable =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def onGetJoinedChannels(message: GetJoinedChannelsRequest): Unit = {
    val GetJoinedChannelsRequest(userId) = message
    this.chatStore.getJoinedChats(userId) map { channels =>
      sender ! GetJoinedChannelsResponse(channels)
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def createChannel(
                                   chatId: Option[String],
                                   ct: ChatType.Value,
                                   membership: ChatMembership.Value,
                                   name: Option[String],
                                   topic: Option[String],
                                   members: Set[DomainUserId],
                                   createdBy: DomainUserId): Try[String] = {

    this.chatStore.createChat(
      chatId, ct, Instant.now(), membership, name.getOrElse(""), topic.getOrElse(""), Some(members), createdBy)
  }

  private[this] def hasPermission(userId: DomainUserId, permission: String): Try[Unit] = {
    Success(())
    if (userId.isConvergence) {
      Success(())
    } else {
      for {
        hasPermission <- permissionsStore.hasPermission(userId, permission)
      } yield {
        if (!hasPermission) {
          Failure(UnauthorizedException("Not authorized"))
        }
      }
    }
  }
}

object ChatManagerActor {

  val RelativePath = "chatManagerActor"

  def props(provider: DomainPersistenceProvider): Props = Props(new ChatManagerActor(provider))

  trait ChatStoreRequest

  case class FindChatInfo(filter: Option[String], offset: Option[Int], limit: Option[Int]) extends ChatStoreRequest

  case class GetChatInfo(chatId: String) extends ChatStoreRequest

  case class CreateChatRequest(
                                chatId: Option[String],
                                createdBy: DomainUserId,
                                chatType: ChatType.Value,
                                membership: ChatMembership.Value,
                                name: Option[String],
                                topic: Option[String],
                                members: Set[DomainUserId]) extends ChatStoreRequest

  case class CreateChatResponse(channelId: String)

  case class GetChannelsRequest(userId: DomainUserId, ids: List[String])

  case class GetChannelsResponse(channels: List[ChatInfo])

  case class ChannelsExistsRequest(userId: DomainUserId, ids: List[String])

  case class ChannelsExistsResponse(channels: List[Boolean])

  case class GetJoinedChannelsRequest(userId: DomainUserId)

  case class GetJoinedChannelsResponse(channels: List[ChatInfo])

  case class GetDirectChannelsRequest(userId: DomainUserId, userLists: List[Set[DomainUserId]])

  case class GetDirectChannelsResponse(channels: List[ChatInfo])

  val DefaultPermissions = List()
}