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

package com.convergencelabs.convergence.server.backend.services.domain.chat

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{ChatPermissionTarget, GlobalPermissionTarget}
import com.convergencelabs.convergence.server.backend.datastore.domain.schema.ChatClass
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatPermissions.ChatPermission
import com.convergencelabs.convergence.server.backend.services.domain.{DomainPersistenceManager, BaseDomainShardedActor}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.chat._
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

class ChatServiceActor private(domainId: DomainId,
                               context: ActorContext[ChatServiceActor.Message],
                               shardRegion: ActorRef[ChatServiceActor.Message],
                               shard: ActorRef[ClusterSharding.ShardCommand],
                               domainPersistenceManager: DomainPersistenceManager,
                               receiveTimeout: FiniteDuration)
  extends BaseDomainShardedActor[ChatServiceActor.Message](domainId, context, shardRegion, shard, domainPersistenceManager, receiveTimeout) {

  import ChatServiceActor._

  override def receiveInitialized(msg: Message): Behavior[Message] = {
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
      case ReceiveTimeout(_) =>
        this.passivate()
    }
  }

  private[this] def onCreateChat(message: CreateChatRequest): Behavior[Message] = {
    val CreateChatRequest(_, chatId, createdBy, chatType, membership, name, topic, members, replyTo) = message
    hasPermission(createdBy, ChatPermissions.Permissions.CreateChat).map { _ =>
      (for {
        id <- createChat(chatId, chatType, membership, name, topic, members, createdBy)
        _ <- this.persistenceProvider.permissionsStore.addUserPermissionsToTarget(
          ChatPermissions.AllExistingChatPermissions, createdBy, ChatPermissionTarget(id))
      } yield {
        Right(id)
      })
        .recover {
          case _: DuplicateValueException =>
            Left(ChatAlreadyExists())
          case cause =>
            error("unexpected error creating chats", cause)
            Left(UnknownError())
        }.foreach(replyTo ! CreateChatResponse(_))
    }

    Behaviors.same
  }

  private[this] def onSearchChats(message: ChatsSearchRequest): Behavior[Message] = {
    val ChatsSearchRequest(_, searchTerm, searchFields, chatType, membership, offset, limit, replyTo) = message
    this.persistenceProvider.chatStore
      .searchChats(searchTerm, searchFields, chatType, membership, offset, limit)
      .map(chat => Right(chat))
      .recover { cause =>
        error("unexpected error searching chats", cause)
        Left(UnknownError())
      }.foreach(replyTo ! ChatsSearchResponse(_))

    Behaviors.same
  }

  private[this] def onGetChat(message: GetChatInfoRequest): Behavior[Message] = {
    val GetChatInfoRequest(_, chatId, replyTo) = message
    this.persistenceProvider.chatStore
      .findChatInfo(chatId)
      .map(_.map(chat => Right(chat)).getOrElse(Left(ChatNotFound())))
      .recover { cause =>
        error("unexpected error getting chat", cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! GetChatInfoResponse(_))

    Behaviors.same
  }

  private[this] def onGetChats(message: GetChatsRequest): Behavior[Message] = {
    val GetChatsRequest(_, user, ids, replyTo) = message
    (for {
      chatInfos <- Try(ids.map(this.persistenceProvider.chatStore.findChatInfo(_).get))
      filtered <- Try {
        chatInfos.collect {
          case Some(chatInfo) if chatInfo.membership == ChatMembership.Public ||
            ChatPermissionResolver.hasChatPermissions(
              this.persistenceProvider.permissionsStore,
              chatInfo.id,
              ChatPermissions.Permissions.JoinChat,
              user).get =>
            chatInfo
        }
      }
    } yield Right(filtered))
      .recover { cause =>
        error("unexpected error getting chats", cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! GetChatsResponse(_))

    Behaviors.same
  }

  private[this] def onExists(message: ChatsExistsRequest): Behavior[Message] = {
    val ChatsExistsRequest(_, user, ids, replyTo) = message
    (for {
      chatInfos <- Try(ids.map(this.persistenceProvider.chatStore.findChatInfo(_).get))
      exists <- Try {
        chatInfos.map {
          case Some(chatInfo) =>
            chatInfo.membership == ChatMembership.Public ||
              ChatPermissionResolver.hasChatPermissions(
                this.persistenceProvider.permissionsStore,
                chatInfo.id,
                ChatPermissions.Permissions.JoinChat,
                user).get
          case None =>
            false
        }
      }
    } yield Right(exists))
      .recover { cause =>
        error("unexpected error checking if chats exist", cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! ChatsExistsResponse(_))

    Behaviors.same
  }

  private[this] def onGetDirect(message: GetDirectChatsRequest): Behavior[Message] = {
    val GetDirectChatsRequest(_, userId, userIdList, replyTo) = message
    Try {
      // The channel must contain the user who is looking it up and any other users
      val members = userIdList.map(l => l + userId)
      members.map(members => getOrCreateDirectChat(userId, members).get)
    }
      .map(chats => Right(chats))
      .recover { cause =>
        error("Unexpected error getting direct chats", cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! GetDirectChatsResponse(_))

    Behaviors.same
  }

  private[this] def getOrCreateDirectChat(requester: DomainUserId, userIds: Set[DomainUserId]): Try[ChatState] = {
    this.persistenceProvider.chatStore.getDirectChatInfoByUsers(userIds) flatMap {
      case Some(c) =>
        // The channel exists, just return it.
        Success(c)
      case None =>
        // Does not exists, so create it.
        createChat(None, ChatType.Direct, ChatMembership.Private, None, None, userIds, requester) flatMap { chatId =>
          // Create was successful, now let's just get the channel.
          this.persistenceProvider.chatStore.getChatState(chatId)
        } recoverWith {
          case DuplicateValueException(ChatClass.Fields.Members, _, _) =>
            // The channel already exists based on the members, this must have been a race condition.
            // So just try to get it again
            this.persistenceProvider.chatStore.getDirectChatInfoByUsers(userIds) flatMap {
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

  private[this] def onGetJoinedChats(message: GetJoinedChatsRequest): Behavior[Message] = {
    val GetJoinedChatsRequest(_, userId, replyTo) = message
    this.persistenceProvider.chatStore
      .getJoinedChannels(userId)
      .map(chats => Right(chats))
      .recover { cause =>
        error("unexpected error getting joined chats", cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! GetJoinedChatsResponse(_))

    Behaviors.same
  }

  private[this] def createChat(chatId: Option[String],
                               chatType: ChatType.Value,
                               membership: ChatMembership.Value,
                               name: Option[String],
                               topic: Option[String],
                               members: Set[DomainUserId],
                               createdBy: DomainUserId): Try[String] = {

    this.persistenceProvider.chatStore.createChat(
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
      this.persistenceProvider.permissionsStore.userHasPermission(userId, GlobalPermissionTarget,permission.p)
    }
  }

  override protected def getDomainId(msg: Message): DomainId = msg.domainId

  override protected def getReceiveTimeoutMessage(): Message = ReceiveTimeout(this.domainId)
}

object ChatServiceActor {

  def apply(domainId: DomainId,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration): Behavior[Message] = Behaviors.setup { context =>
    new ChatServiceActor(
      domainId,
      context,
      shardRegion,
      shard,
      domainPersistenceManager,
      receiveTimeout)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatsExistsRequest], name = "chats_exist"),
    new JsonSubTypes.Type(value = classOf[ChatsSearchRequest], name = "search"),
    new JsonSubTypes.Type(value = classOf[CreateChatRequest], name = "create_chat"),
    new JsonSubTypes.Type(value = classOf[GetChatInfoRequest], name = "get_chat_info"),
    new JsonSubTypes.Type(value = classOf[GetChatsRequest], name = "get_chats"),
    new JsonSubTypes.Type(value = classOf[GetDirectChatsRequest], name = "get_direct"),
    new JsonSubTypes.Type(value = classOf[GetJoinedChatsRequest], name = "get_joined"),
  ))
  sealed trait Message extends CborSerializable {
    val domainId: DomainId
  }

  private final case class ReceiveTimeout(domainId: DomainId) extends Message

  //
  // ChatSearch
  //
  final case class ChatsSearchRequest(domainId: DomainId,
                                      searchTerm: Option[String],
                                      searchFields: Option[Set[String]],
                                      chatType: Option[Set[ChatType.Value]],
                                      membership: Option[ChatMembership.Value],
                                      @JsonDeserialize(contentAs = classOf[Long])
                                      offset: QueryOffset,
                                      @JsonDeserialize(contentAs = classOf[Long])
                                      limit: QueryLimit,
                                      replyTo: ActorRef[ChatsSearchResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait ChatsSearchError

  final case class ChatsSearchResponse(chats: Either[ChatsSearchError, PagedData[ChatState]]) extends CborSerializable

  //
  // GetChatInfo
  //
  final case class GetChatInfoRequest(domainId: DomainId,
                                      chatId: String,
                                      replyTo: ActorRef[GetChatInfoResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFound], name = "chat_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetChatInfoError

  final case class ChatNotFound() extends GetChatInfoError

  final case class GetChatInfoResponse(chat: Either[GetChatInfoError, ChatState]) extends CborSerializable

  //
  // Create Chat
  //
  final case class CreateChatRequest(domainId: DomainId,
                                     chatId: Option[String],
                                     createdBy: DomainUserId,
                                     @JsonScalaEnumeration(classOf[ChatTypeReference])
                                     chatType: ChatType.Value,
                                     @JsonScalaEnumeration(classOf[ChatMembershipTypeReference])
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
  final case class GetChatsRequest(domainId: DomainId,
                                   userId: DomainUserId,
                                   ids: Set[String], replyTo: ActorRef[GetChatsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetChatsError

  final case class GetChatsResponse(chatInfo: Either[GetChatsError, Set[ChatState]]) extends CborSerializable

  //
  // Chat Exists
  //
  final case class ChatsExistsRequest(domainId: DomainId,
                                      userId: DomainUserId,
                                      ids: List[String], replyTo: ActorRef[ChatsExistsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait ChatsExistsError

  final case class ChatsExistsResponse(exists: Either[ChatsExistsError, List[Boolean]]) extends CborSerializable

  //
  // GetJoinedChats
  //
  final case class GetJoinedChatsRequest(domainId: DomainId,
                                         userId: DomainUserId,
                                         replyTo: ActorRef[GetJoinedChatsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetJoinedChatsError

  final case class GetJoinedChatsResponse(chatInfo: Either[GetJoinedChatsError, Set[ChatState]]) extends CborSerializable

  //
  // GetDirectChats
  //
  final case class GetDirectChatsRequest(domainId: DomainId,
                                         userId: DomainUserId,
                                         userLists: Set[Set[DomainUserId]],
                                         replyTo: ActorRef[GetDirectChatsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetDirectChatsError

  final case class GetDirectChatsResponse(chatInfo: Either[GetDirectChatsError, Set[ChatState]]) extends CborSerializable

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