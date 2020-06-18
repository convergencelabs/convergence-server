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
import com.convergencelabs.convergence.server.datastore.domain.PermissionsStore.ChatPermissionTarget
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.datastore.domain.schema.ChatClass
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatPermissions.ChatPermission
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

class ChatManagerActor private(context: ActorContext[ChatManagerActor.Message],
                               chatStore: ChatStore,
                               permissionsStore: PermissionsStore)
  extends AbstractBehavior[ChatManagerActor.Message](context) with Logging {

  import ChatManagerActor._

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
    hasPermission(createdBy, ChatPermissions.Permissions.CreateChat).map { _ =>
      (for {
        id <- createChat(chatId, chatType, membership, name, topic, members, createdBy)
        _ <- permissionsStore.addPermissionsForUser(ChatPermissions.AllExistingChatPermissions, createdBy, ChatPermissionTarget(id))
      } yield {
        CreateChatResponse(Right(id))
      })
        .recover {
          case _: DuplicateValueException =>
            CreateChatResponse(Left(ChatAlreadyExists()))
          case cause =>
            error("unexpected error creating chats", cause)
            CreateChatResponse(Left(UnknownError()))
        }.foreach(replyTo ! _)
    }
  }

  private[this] def onSearchChats(message: ChatsSearchRequest): Unit = {
    val ChatsSearchRequest(searchTerm, searchFields, chatType, membership, offset, limit, replyTo) = message
    chatStore
      .searchChats(searchTerm, searchFields, chatType, membership, offset, limit)
      .map(chat => ChatsSearchResponse(Right(chat)))
      .recover { cause =>
        error("unexpected error searching chats", cause)
        ChatsSearchResponse(Left(UnknownError()))
      }.foreach(replyTo ! _)
  }

  private[this] def onGetChat(message: GetChatInfoRequest): Unit = {
    val GetChatInfoRequest(chatId, replyTo) = message
    chatStore
      .findChatInfo(chatId)
      .map(_.map(chat => Right(chat)).getOrElse(Left(ChatNotFound())))
      .map(GetChatInfoResponse)
      .recover { cause =>
        error("unexpected error getting chat", cause)
        GetChatInfoResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetChats(message: GetChatsRequest): Unit = {
    val GetChatsRequest(user, ids, replyTo) = message
    (for {
      chatInfos <- Try(ids.map(chatStore.findChatInfo(_).get))
      filtered <- Try {
        chatInfos.collect {
          case Some(chatInfo) if chatInfo.membership == ChatMembership.Public ||
            ChatPermissionResolver.hasChatPermissions(permissionsStore, chatInfo.id, ChatPermissions.Permissions.JoinChat, user).get =>
            chatInfo
        }
      }
    } yield GetChatsResponse(Right(filtered)))
      .recover { cause =>
        error("unexpected error getting chats", cause)
        GetChatsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onExists(message: ChatsExistsRequest): Unit = {
    val ChatsExistsRequest(user, ids, replyTo) = message
    (for {
      chatInfos <- Try(ids.map(chatStore.findChatInfo(_).get))
      exists <- Try {
        chatInfos.map {
          case Some(chatInfo) =>
            chatInfo.membership == ChatMembership.Public ||
              ChatPermissionResolver.hasChatPermissions(permissionsStore, chatInfo.id, ChatPermissions.Permissions.JoinChat, user).get
          case None =>
            false
        }
      }
    } yield ChatsExistsResponse(Right(exists)))
      .recover { cause =>
        error("unexpected error checking if chats exist", cause)
        ChatsExistsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetDirect(message: GetDirectChatsRequest): Unit = {
    val GetDirectChatsRequest(userId, userIdList, replyTo) = message
    Try {
      // The channel must contain the user who is looking it up and any other users
      val members = userIdList.map(l => l + userId)
      members.map(members => getOrCreateDirectChat(userId, members).get)
    }
      .map(chats => GetDirectChatsResponse(Right(chats)))
      .recover { cause =>
        error("Unexpected error getting direct chats", cause)
        GetDirectChatsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
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
    this.chatStore
      .getJoinedChannels(userId)
      .map(chats => GetJoinedChatsResponse(Right(chats)))
      .recover { cause =>
        error("unexpected error getting joined chats", cause)
        GetJoinedChatsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
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

  private[this] def hasPermission(userId: DomainUserId, permission: ChatPermission): Try[Boolean] = {
    if (userId.isConvergence) {
      Success(true)
    } else {
      permissionsStore.userHasGlobalPermission(userId, permission.p)
    }
  }
}

object ChatManagerActor {

  def apply(chatStore: ChatStore,
            permissionsStore: PermissionsStore): Behavior[Message] =
    Behaviors.setup(context => new ChatManagerActor(context, chatStore, permissionsStore))


  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  //
  // ChatSearch
  //
  final case class ChatsSearchRequest(searchTerm: Option[String],
                                      searchFields: Option[Set[String]],
                                      chatType: Option[Set[ChatType.Value]],
                                      membership: Option[ChatMembership.Value],
                                      offset: QueryOffset,
                                      limit: QueryLimit,
                                      replyTo: ActorRef[ChatsSearchResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait ChatsSearchError

  final case class ChatsSearchResponse(chats: Either[ChatsSearchError, PagedData[ChatInfo]]) extends CborSerializable

  //
  // GetChatInfo
  //
  final case class GetChatInfoRequest(chatId: String, replyTo: ActorRef[GetChatInfoResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFound], name = "chat_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetChatInfoError

  final case class ChatNotFound() extends GetChatInfoError

  final case class GetChatInfoResponse(chat: Either[GetChatInfoError, ChatInfo]) extends CborSerializable

  //
  // Create Chat
  //
  final case class CreateChatRequest(chatId: Option[String],
                                     createdBy: DomainUserId,
                                     chatType: ChatType.Value,
                                     membership: ChatMembership.Value,
                                     name: Option[String],
                                     topic: Option[String],
                                     members: Set[DomainUserId],
                                     replyTo: ActorRef[CreateChatResponse]) extends Message


  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatAlreadyExists], name = "chat_exists"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait CreateChatError

  final case class ChatAlreadyExists() extends CreateChatError

  final case class CreateChatResponse(chatId: Either[CreateChatError, String]) extends CborSerializable

  //
  // Get Chats
  //
  final case class GetChatsRequest(userId: DomainUserId, ids: Set[String], replyTo: ActorRef[GetChatsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetChatsError

  final case class GetChatsResponse(chatInfo: Either[GetChatsError, Set[ChatInfo]]) extends CborSerializable

  //
  // Chat Exists
  //
  final case class ChatsExistsRequest(userId: DomainUserId, ids: List[String], replyTo: ActorRef[ChatsExistsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait ChatsExistsError

  final case class ChatsExistsResponse(exists: Either[ChatsExistsError, List[Boolean]]) extends CborSerializable

  //
  // GetJoinedChats
  //
  final case class GetJoinedChatsRequest(userId: DomainUserId, replyTo: ActorRef[GetJoinedChatsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetJoinedChatsError

  final case class GetJoinedChatsResponse(chatInfo: Either[GetJoinedChatsError, Set[ChatInfo]]) extends CborSerializable

  //
  // GetDirectChats
  //
  final case class GetDirectChatsRequest(userId: DomainUserId,
                                         userLists: Set[Set[DomainUserId]],
                                         replyTo: ActorRef[GetDirectChatsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetDirectChatsError

  final case class GetDirectChatsResponse(chatInfo: Either[GetDirectChatsError, Set[ChatInfo]]) extends CborSerializable

  //
  // Common Errors
  //
  final case class UnknownError() extends AnyRef
    with CreateChatError
    with ChatsSearchError
    with GetChatInfoError
    with GetChatsError
    with ChatsExistsError
    with GetJoinedChatsError
    with GetDirectChatsError

}