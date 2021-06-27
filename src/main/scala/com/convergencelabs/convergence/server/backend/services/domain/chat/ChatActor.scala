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
import com.convergencelabs.convergence.common.{Ok, PagedDataResult}
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.backend.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.backend.datastore.domain.chat.{ChatNotFoundException, ChatStore}
import com.convergencelabs.convergence.server.backend.services.domain.DomainPersistenceManagerActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.processors._
import com.convergencelabs.convergence.server.backend.services.domain.permissions.AllPermissions
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.chat.{ChatEvent, ChatMembership, ChatState, ChatType}
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.util.actor.{ShardedActor, ShardedActorStatUpPlan, StartUpRequired}
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo, JsonTypeName}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.util.{Failure, Try}


/**
 * The [[ChatActor]] represents a single unique chat instance in the system. It
 * is sharded across the backend nodes and can represent a chat channel or a
 * chat room. The handling of messages is delegated to a ChatMessageProcessor
 * which implements the specific business logic of each type of chat.
 *
 * @param context            The ActorContext this actor is created in.
 * @param shardRegion        The ActorRef to send messages to the chat share
 *                           region.
 * @param shard              The ActorRef to send messages to this sharded
 *                           actors host shard.
 * @param chatDeliveryRegion The shard region for message delivery to users.
 */
class ChatActor private(domainId: DomainId,
                        chatId: String,
                        context: ActorContext[ChatActor.Message],
                        shardRegion: ActorRef[ChatActor.Message],
                        shard: ActorRef[ClusterSharding.ShardCommand],
                        chatDeliveryRegion: ActorRef[ChatDeliveryActor.Send],
                        receiveTimeout: FiniteDuration)
  extends ShardedActor[ChatActor.Message](
    context,
    shardRegion,
    shard,
    entityDescription = s"${domainId.namespace}/${domainId.domainId}/$chatId") {

  import ChatActor._

  // Here None signifies that the chat is not initialized, or it doesn't exist
  private[this] var messageProcessor: Option[ChatMessageProcessor] = None


  protected def initialize(message: Message): Try[ShardedActorStatUpPlan] = {
    (for {
      provider <- DomainPersistenceManagerActor.acquirePersistenceProvider(context.self, context.system, domainId)
      state <- createState(chatId, provider.chatStore)
    } yield {
      debug("Initial Chat State: " + state)
      state.chatType match {
        case ChatType.Room =>
          val clientManager = new ChatRoomClientManager()
          val clientWatcher = context.spawnAnonymous(ChatRoomClientWatcher(context.self, domainId, state.id, clientManager))

          val mp = new ChatRoomMessageProcessor(
            state,
            provider.chatStore,
            provider.permissionsStore,
            domainId,
            clientManager,
            clientWatcher)

          this.messageProcessor = Some(mp)

        case ChatType.Channel =>
          context.setReceiveTimeout(receiveTimeout, ReceiveTimeout(this.domainId, this.chatId))
          state.membership match {
            case ChatMembership.Private =>
              this.messageProcessor = Some(new PrivateChannelMessageProcessor(
                state,
                provider.chatStore,
                provider.permissionsStore,
                domainId,
                chatDeliveryRegion))

            case ChatMembership.Public =>
              this.messageProcessor = Some(new PublicChannelMessageProcessor(
                state,
                provider.chatStore,
                provider.permissionsStore,
                domainId,
                chatDeliveryRegion))
          }

        case ChatType.Direct =>
          context.setReceiveTimeout(receiveTimeout, ReceiveTimeout(this.domainId, this.chatId))
          this.messageProcessor = Some(new DirectChatMessageProcessor(
            state,
            provider.chatStore,
            provider.permissionsStore,
            domainId,
            chatDeliveryRegion
          ))
      }

      this.messageProcessor.foreach(_.startup())
      StartUpRequired
    })
      .recoverWith {
        case NonFatal(cause) =>
          error(s"error initializing chat channel: '$domainId/$chatId'", cause)
          Failure(cause)
      }
  }

  def receiveInitialized(msg: Message): Behavior[Message] = {
    msg match {
      case ReceiveTimeout(_, _) =>
        this.onReceiveTimeout()
      case message: ChatRequestMessage =>
        processChatMessage(message)
    }
    Behaviors.same
  }

  override def postStop(): Unit = {
    super.postStop()

    messageProcessor.foreach(_.shutdown())
    DomainPersistenceManagerActor.releasePersistenceProvider(context.self, context.system, domainId)
  }

  private[this] def processChatMessage(message: ChatRequestMessage): Behavior[_ <: Message] = {
    this.messageProcessor match {
      case Some(messageProcessor) =>
        messageProcessor.processChatRequestMessage(message) match {
          case ChatMessageProcessor.Same =>
            Behaviors.same
          case ChatMessageProcessor.Passivate =>
            this.passivate()
          case ChatMessageProcessor.Stop =>
            Behaviors.stopped
        }

      case None =>
        error("The message processor must be set before processing messages")
        Behaviors.stopped
    }
  }

  private[this] def onReceiveTimeout(): Behavior[Message] = {
    debug("Receive timeout reached, asking shard region to passivate")
    this.passivate()
  }

  private[this] def createState(chatId: String, chatStore: ChatStore): Try[ChatState] = {
    chatStore
      .getChatState(chatId)
      .recoverWith {
        case cause: EntityNotFoundException =>
          Failure(ChatNotFoundException(chatId))
      }
  }
}

object ChatActor {
  def apply(domainId: DomainId,
            chatId: String,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            chatDeliveryRegion: ActorRef[ChatDeliveryActor.Send],
            receiveTimeout: FiniteDuration): Behavior[Message] = Behaviors.setup(context =>
      new ChatActor(
        domainId,
        chatId,
        context,
        shardRegion,
        shard,
        chatDeliveryRegion,
        receiveTimeout))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  /**
   * The trait of all messages sent to the ChatActor.
   */
  sealed trait Message extends CborSerializable {
    val domainId: DomainId
    val chatId: String
  }

  /**
   * Signifies that a receive timeout occurred such that the chat actor can
   * passivate.
   */
  private final case class ReceiveTimeout(domainId: DomainId, chatId: String) extends Message

  // Incoming Messages

  sealed trait ChatRequestMessage extends Message {
    def replyTo: ActorRef[_]
  }


  sealed trait ChatEventRequest[T] extends ChatRequestMessage {
    val replyTo: ActorRef[T]
    val requester: DomainUserId
  }

  //
  // JoinChat
  //
  final case class JoinChatRequest(domainId: DomainId,
                                   chatId: String,
                                   requester: DomainUserId,
                                   client: ActorRef[ChatClientActor.OutgoingMessage],
                                   replyTo: ActorRef[JoinChatResponse]) extends ChatEventRequest[JoinChatResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatAlreadyJoinedError]),
    new JsonSubTypes.Type(value = classOf[ChatOperationNotSupported]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait JoinChatError

  final case class JoinChatResponse(info: Either[JoinChatError, ChatState]) extends CborSerializable

  //
  // LeaveChannel
  //
  final case class LeaveChatRequest(domainId: DomainId,
                                    chatId: String,
                                    requester: DomainUserId,
                                    client: ActorRef[ChatClientActor.OutgoingMessage],
                                    replyTo: ActorRef[LeaveChatResponse]) extends ChatEventRequest[LeaveChatResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[ChatOperationNotSupported]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait LeaveChatError

  final case class LeaveChatResponse(response: Either[LeaveChatError, Ok]) extends CborSerializable

  //
  // AddUserToChannel
  //
  final case class AddUserToChatRequest(domainId: DomainId,
                                        chatId: String,
                                        requester: DomainUserId,
                                        userToAdd: DomainUserId,
                                        replyTo: ActorRef[AddUserToChatResponse]) extends ChatEventRequest[AddUserToChatResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[AlreadyAMemberError]),
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[ChatOperationNotSupported]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait AddUserToChatError

  @JsonTypeName("already_member")
  final case class AlreadyAMemberError() extends AddUserToChatError

  final case class AddUserToChatResponse(response: Either[AddUserToChatError, Ok]) extends CborSerializable

  //
  // RemoveUserFromChannel
  //
  final case class RemoveUserFromChatRequest(domainId: DomainId,
                                             chatId: String,
                                             requester: DomainUserId,
                                             userToRemove: DomainUserId,
                                             replyTo: ActorRef[RemoveUserFromChatResponse]) extends ChatEventRequest[RemoveUserFromChatResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[NotAMemberError]),
    new JsonSubTypes.Type(value = classOf[CantRemoveSelfError]),
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[ChatOperationNotSupported]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait RemoveUserFromChatError

  @JsonTypeName("not_a_member")
  final case class NotAMemberError() extends RemoveUserFromChatError

  @JsonTypeName("remove_self")
  final case class CantRemoveSelfError() extends RemoveUserFromChatError

  final case class RemoveUserFromChatResponse(response: Either[RemoveUserFromChatError, Ok]) extends CborSerializable

  //
  // SetChatName
  //
  final case class SetChatNameRequest(domainId: DomainId,
                                      chatId: String,
                                      requester: DomainUserId,
                                      name: String,
                                      replyTo: ActorRef[SetChatNameResponse]) extends ChatEventRequest[SetChatNameResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait SetChatNameError

  final case class SetChatNameResponse(response: Either[SetChatNameError, Ok]) extends CborSerializable

  //
  // SetChatTopic
  //
  final case class SetChatTopicRequest(domainId: DomainId,
                                       chatId: String,
                                       requester: DomainUserId,
                                       topic: String,
                                       replyTo: ActorRef[SetChatTopicResponse]) extends ChatEventRequest[SetChatTopicResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait SetChatTopicError

  final case class SetChatTopicResponse(response: Either[SetChatTopicError, Ok]) extends CborSerializable

  //
  // MarkChatsEventsSeenRequest
  //
  final case class MarkChatsEventsSeenRequest(domainId: DomainId,
                                              chatId: String,
                                              requester: DomainUserId,
                                              eventNumber: Long,
                                              replyTo: ActorRef[MarkChatsEventsSeenResponse]) extends ChatEventRequest[MarkChatsEventsSeenResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait MarkChatsEventsSeenError

  final case class MarkChatsEventsSeenResponse(response: Either[MarkChatsEventsSeenError, Ok]) extends CborSerializable

  //
  // PublishChatMessage
  //
  final case class PublishChatMessageRequest(domainId: DomainId,
                                             chatId: String,
                                             requester: DomainUserId,
                                             message: String,
                                             replyTo: ActorRef[PublishChatMessageResponse]) extends ChatEventRequest[PublishChatMessageResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait PublishChatMessageError

  final case class PublishChatMessageAck(eventNumber: Long, timestamp: Instant)

  final case class PublishChatMessageResponse(response: Either[PublishChatMessageError, PublishChatMessageAck]) extends CborSerializable


  /////////////////////////////////////////////////////////////////////////////
  // Chat Permissions Messages
  /////////////////////////////////////////////////////////////////////////////

  sealed trait ChatPermissionsRequest[R] extends ChatRequestMessage {
    val replyTo: ActorRef[R]
    val requester: DomainSessionAndUserId
  }

  //
  // AddChatPermissions
  //
  final case class AddChatPermissionsRequest(domainId: DomainId,
                                             chatId: String,
                                             requester: DomainSessionAndUserId,
                                             world: Option[Set[String]],
                                             user: Option[Map[DomainUserId, Set[String]]],
                                             group: Option[Map[String, Set[String]]],
                                             replyTo: ActorRef[AddChatPermissionsResponse]) extends ChatPermissionsRequest[AddChatPermissionsResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait AddChatPermissionsError

  final case class AddChatPermissionsResponse(response: Either[AddChatPermissionsError, Ok]) extends CborSerializable

  //
  // RemoveChatPermissions
  //
  final case class RemoveChatPermissionsRequest(domainId: DomainId,
                                                chatId: String,
                                                requester: DomainSessionAndUserId,
                                                world: Option[Set[String]],
                                                user: Option[Map[DomainUserId, Set[String]]],
                                                group: Option[Map[String, Set[String]]],
                                                replyTo: ActorRef[RemoveChatPermissionsResponse]) extends ChatPermissionsRequest[RemoveChatPermissionsResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait RemoveChatPermissionsError

  final case class RemoveChatPermissionsResponse(response: Either[RemoveChatPermissionsError, Ok]) extends CborSerializable

  //
  // SetChatPermissions
  //
  final case class SetChatPermissionsRequest(domainId: DomainId,
                                             chatId: String,
                                             requester: DomainSessionAndUserId,
                                             world: Option[Set[String]],
                                             user: Option[Map[DomainUserId, Set[String]]],
                                             group: Option[Map[String, Set[String]]],
                                             replyTo: ActorRef[SetChatPermissionsResponse]) extends ChatPermissionsRequest[SetChatPermissionsResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait SetChatPermissionsError

  final case class SetChatPermissionsResponse(response: Either[SetChatPermissionsError, Ok]) extends CborSerializable

  //
  // ResolveSessionPermissions
  //
  final case class ResolveSessionPermissionsRequest(domainId: DomainId,
                                                    chatId: String,
                                                    requester: DomainSessionAndUserId,
                                                    replyTo: ActorRef[ResolveSessionPermissionsResponse]) extends ChatPermissionsRequest[ResolveSessionPermissionsResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait GetSessionPermissionsError

  final case class ResolveSessionPermissionsResponse(permissions: Either[GetSessionPermissionsError, Set[String]]) extends CborSerializable

  //
  // GetWorldChatPermissions
  //
  final case class GetPermissionsRequest(domainId: DomainId,
                                                  chatId: String,
                                                  requester: DomainSessionAndUserId,
                                                  replyTo: ActorRef[GetPermissionsResponse]) extends ChatPermissionsRequest[GetPermissionsResponse]

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait GetPermissionsError

  final case class GetPermissionsResponse(permissions: Either[GetPermissionsError, AllPermissions]) extends CborSerializable


  /*
   * General Messages
   */

  //
  // GetChatHistory
  //
  final case class GetChatHistoryRequest(domainId: DomainId,
                                         chatId: String,
                                         requester: Option[DomainSessionAndUserId],
                                         @JsonDeserialize(contentAs = classOf[Long])
                                         offset: QueryOffset,
                                         @JsonDeserialize(contentAs = classOf[Long])
                                         limit: QueryLimit,
                                         @JsonDeserialize(contentAs = classOf[Long])
                                         startEvent: Option[Long],
                                         forward: Option[Boolean],
                                         eventTypes: Option[Set[String]],
                                         messageFilter: Option[String] = None,
                                         replyTo: ActorRef[GetChatHistoryResponse]) extends ChatRequestMessage

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[ChatNotJoinedError]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait GetChatHistoryError

  final case class PagedChatEvents(data: List[ChatEvent], offset: Long, count: Long) extends PagedDataResult[ChatEvent]

  final case class GetChatHistoryResponse(events: Either[GetChatHistoryError, PagedChatEvents]) extends CborSerializable


  //
  // RemoveChatRequest
  //
  final case class RemoveChatRequest(domainId: DomainId,
                                     chatId: String,
                                     requester: DomainUserId,
                                     replyTo: ActorRef[RemoveChatResponse]) extends ChatRequestMessage

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ChatNotFoundError]),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait RemoveChatError

  final case class RemoveChatResponse(response: Either[RemoveChatError, Ok]) extends CborSerializable


  //
  // Common Errors
  //

  @JsonTypeName("already_joined")
  final case class ChatAlreadyJoinedError() extends AnyRef
    with JoinChatError

  sealed trait CommonErrors extends AnyRef
    with RemoveChatError
    with JoinChatError
    with LeaveChatError
    with AddUserToChatError
    with RemoveUserFromChatError
    with SetChatNameError
    with SetChatTopicError
    with MarkChatsEventsSeenError
    with PublishChatMessageError
    with AddChatPermissionsError
    with RemoveChatPermissionsError
    with SetChatPermissionsError
    with GetSessionPermissionsError
    with GetPermissionsError
    with GetChatHistoryError

  @JsonTypeName("unknown")
  final case class UnknownError() extends CommonErrors

  @JsonTypeName("unauthorized")
  final case class UnauthorizedError() extends CommonErrors

  @JsonTypeName("not_found")
  final case class ChatNotFoundError() extends CommonErrors

  @JsonTypeName("not_Supported")
  final case class ChatOperationNotSupported(reason: String) extends AnyRef
    with AddUserToChatError
    with RemoveUserFromChatError
    with JoinChatError
    with LeaveChatError


  @JsonTypeName("not_joined")
  final case class ChatNotJoinedError() extends AnyRef
    with LeaveChatError
    with AddUserToChatError
    with RemoveUserFromChatError
    with SetChatNameError
    with SetChatTopicError
    with MarkChatsEventsSeenError
    with PublishChatMessageError
    with AddChatPermissionsError
    with RemoveChatPermissionsError
    with SetChatPermissionsError
    with GetSessionPermissionsError
    with GetPermissionsError
    with GetChatHistoryError
}


