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

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.datastore.domain.schema.ChatClass
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor.ChatAlreadyExistsException
import com.convergencelabs.convergence.server.domain.chat.ChatStateManager.ChatPermissions
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

class ChatManagerActor private[domain](context: ActorContext[ChatManagerActor.Message],
                                       private[this] val provider: DomainPersistenceProvider)
  extends AbstractBehavior[ChatManagerActor.Message](context) with Logging {

  import ChatManagerActor._

  private[this] val chatStore: ChatStore = provider.chatStore
  private[this] val permissionsStore: PermissionsStore = provider.permissionsStore

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case message: CreateChatRequest =>
        onCreateChat(message)
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
      case message: GetChatInfoRequest =>
        onGetChat(message)
    }

    Behaviors.same
  }

  private[this] def onCreateChat(message: CreateChatRequest): Unit = {
    val CreateChatRequest(chatId, createdBy, chatType, membership, name, topic, members, replyTo) = message
    hasPermission(createdBy, ChatPermissions.CreateChannel).map { _ =>
      (for {
        id <- createChat(chatId, chatType, membership, name, topic, members, createdBy)
        forRecord <- chatStore.getChatRid(id)
        _ <- permissionsStore.addUserPermissions(ChatStateManager.AllChatPermissions, createdBy, Some(forRecord))
      } yield {
        CreateChatSuccess(id)
      }) match {
        case Success(response) =>
          replyTo ! response
        case Failure(DuplicateValueException(_, _, _)) =>
          val cId = chatId.get
          replyTo ! RequestFailure(ChatAlreadyExistsException(cId))
        case Failure(cause) =>
          replyTo ! RequestFailure(cause)
      }
    }
  }

  private[this] def onSearchChats(message: ChatsSearchRequest): Unit = {
    val ChatsSearchRequest(searchTerm, searchFields, chatType, membership, offset, limit, replyTo) = message
    chatStore.searchChats(searchTerm, searchFields, chatType, membership, offset, limit) match {
      case Success(info) =>
        replyTo ! ChatsSearchSuccess(info)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetChat(message: GetChatInfoRequest): Unit = {
    val GetChatInfoRequest(chatId, replyTo) = message
    chatStore.getChatInfo(chatId) match {
      case Success(info) =>
        replyTo ! GetChatInfoSuccess(info)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetChats(message: GetChatsRequest): Unit = {
    val GetChatsRequest(sk, ids, replyTo) = message
    Try(ids.map(chatStore.findChatInfo(_).get)).flatMap { chatInfos =>
      Try {
        chatInfos.collect {
          case Some(chatInfo) if chatInfo.membership == ChatMembership.Public || hasPermission(sk, chatInfo.id, ChatPermissions.JoinChannel).get =>
            chatInfo
        }
      }
    } match {
      case Success(chatInfos) =>
        replyTo ! GetChatsSuccess(chatInfos)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onExists(message: ChatsExistsRequest): Unit = {
    val ChatsExistsRequest(sk, ids, replyTo) = message
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
        replyTo ! ChatsExistsSuccess(chatInfos)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetDirect(message: GetDirectChatsRequest): Unit = {
    val GetDirectChatsRequest(userId, userIdList, replyTo) = message
    Try {
      // The channel must contain the user who is looking it up and any other users
      val members = userIdList.map(l => l + userId)
      members.map(members => getOrCreateDirectChat(userId, members).get)
    } match {
      case Success(chats) =>
        replyTo ! GetDirectChatsSuccess(chats)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def getOrCreateDirectChat(requester: DomainUserId, userIds: Set[DomainUserId]): Try[ChatInfo] = {
    chatStore.getDirectChatInfoByUsers(userIds) flatMap {
      case Some(c) =>
        // The channel exists, just return it.
        Success(c)
      case None =>
        // Does not exists, so create it.
        createChat(None, ChatType.Direct, ChatMembership.Private, None, None, userIds, requester) flatMap { chatId =>
          // Create was successful, now let's just get the channel.
          chatStore.getChatInfo(chatId)
        } recoverWith {
          case DuplicateValueException(ChatClass.Fields.Members, _, _) =>
            // The channel already exists based on the members, this must have been a race condition.
            // So just try to get it again
            chatStore.getDirectChatInfoByUsers(userIds) flatMap {
              case Some(c) =>
                // Yup it's there.
                Success(c)
              case None =>
                // We are now in a strange world where we are told the channel exists, but can
                // not look it up.
                Failure(new IllegalStateException("Can not create direct channel, due to an unexpected error"))
            }
        }
    }
  }

  private[this] def onGetJoinedChats(message: GetJoinedChatsRequest): Unit = {
    val GetJoinedChatsRequest(userId, replyTo) = message
    this.chatStore.getJoinedChannels(userId)  match {
      case Success(chats) =>
        replyTo ! GetJoinedChatsSuccess(chats)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def createChat(chatId: Option[String],
                               chatType: ChatType.Value,
                               membership: ChatMembership.Value,
                               name: Option[String],
                               topic: Option[String],
                               members: Set[DomainUserId],
                               createdBy: DomainUserId): Try[String] = {

    this.chatStore.createChat(
      chatId,
      chatType,
      Instant.now(),
      membership,
      name.getOrElse(""),
      topic.getOrElse(""),
      Some(members),
      createdBy)
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

  def apply(provider: DomainPersistenceProvider): Behavior[Message] = Behaviors.setup { context =>
    new ChatManagerActor(context, provider)
  }

  trait Message extends CborSerializable with DomainRestMessageBody

  case class ChatsSearchRequest(searchTerm: Option[String],
                                searchFields: Option[Set[String]],
                                chatType: Option[Set[ChatType.Value]],
                                membership: Option[ChatMembership.Value],
                                offset: Option[Long],
                                limit: Option[Long],
                                replyTo: ActorRef[ChatsSearchResponse]) extends Message

  sealed trait ChatsSearchResponse extends CborSerializable

  case class ChatsSearchSuccess(chats: PagedData[ChatInfo]) extends ChatsSearchResponse

  case class GetChatInfoRequest(chatId: String, replyTo: ActorRef[GetChatInfoResponse]) extends Message

  sealed trait GetChatInfoResponse extends CborSerializable

  case class GetChatInfoSuccess(chat: ChatInfo) extends GetChatInfoResponse

  case class CreateChatRequest(chatId: Option[String],
                               createdBy: DomainUserId,
                               chatType: ChatType.Value,
                               membership: ChatMembership.Value,
                               name: Option[String],
                               topic: Option[String],
                               members: Set[DomainUserId],
                               replyTo: ActorRef[CreateChatResponse]) extends Message

  sealed trait CreateChatResponse extends CborSerializable

  case class CreateChatSuccess(channelId: String) extends CreateChatResponse

  case class GetChatsRequest(userId: DomainUserId, ids: Set[String], replyTo: ActorRef[GetChatsResponse]) extends Message

  sealed trait GetChatsResponse extends CborSerializable

  case class GetChatsSuccess(channels: Set[ChatInfo]) extends GetChatsResponse

  case class ChatsExistsRequest(userId: DomainUserId, ids: List[String], replyTo: ActorRef[ChatsExistsResponse]) extends Message

  sealed trait ChatsExistsResponse extends CborSerializable

  case class ChatsExistsSuccess(chatIds: List[Boolean]) extends ChatsExistsResponse

  case class GetJoinedChatsRequest(userId: DomainUserId, replyTo: ActorRef[GetJoinedChatsResponse]) extends Message

  sealed trait GetJoinedChatsResponse extends CborSerializable

  case class GetJoinedChatsSuccess(channels: Set[ChatInfo]) extends GetJoinedChatsResponse


  case class GetDirectChatsRequest(userId: DomainUserId,
                                   userLists: Set[Set[DomainUserId]],
                                   replyTo: ActorRef[GetDirectChatsResponse]) extends Message

  sealed trait GetDirectChatsResponse extends CborSerializable

  case class GetDirectChatsSuccess(channels: Set[ChatInfo]) extends GetDirectChatsResponse

  case class RequestFailure(cause: Throwable) extends CborSerializable
    with CreateChatResponse
    with ChatsSearchResponse
    with GetChatInfoResponse
    with GetChatsResponse
    with ChatsExistsResponse
    with GetJoinedChatsResponse
    with GetDirectChatsResponse
}