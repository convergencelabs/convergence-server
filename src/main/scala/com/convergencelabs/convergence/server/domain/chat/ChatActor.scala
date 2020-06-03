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

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.actor.{CborSerializable, ShardedActor, ShardedActorStatUpPlan, StartUpRequired}
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.datastore.domain.{ChatEvent, ChatInfo, ChatMembership, ChatType, DomainPersistenceManagerActor}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId, DomainUserSessionId}
import grizzled.slf4j.Logging

import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
 * The [[ChatActor]] represents a single unique chat instance in the system. It
 * is sharded across the backend nodes and can represent a chat channel or a
 * chat room. The handling of messages is delegated to a ChatMessageProcessor
 * which implements the specific business logic of each type of chat.
 */
class ChatActor private[domain](context: ActorContext[ChatActor.Message],
                                shardRegion: ActorRef[ChatActor.Message],
                                shard: ActorRef[ClusterSharding.ShardCommand])
  extends ShardedActor[ChatActor.Message](context, shardRegion, shard) with Logging {

  import ChatActor._


  private[this] var domainId: DomainId = _
  private[this] var chatId: String = _

  // Here None signifies that the channel does not exist.
  private[this] var channelManager: Option[ChatStateManager] = None
  private[this] var messageProcessor: Option[ChatMessageProcessor] = None

  protected def setIdentityData(message: Message): Try[String] = {
    this.domainId = message.domainId
    this.chatId = message.chatId
    Success(s"${domainId.namespace}/${domainId.domainId}/${this.chatId}")
  }

  protected def initialize(message: Message): Try[ShardedActorStatUpPlan] = {
    DomainPersistenceManagerActor.acquirePersistenceProvider(context.self, context.system, domainId) flatMap { provider =>
      debug(s"Chat Channel acquired persistence, creating channel manager: '$domainId/$chatId'")
      ChatStateManager.create(chatId, provider.chatStore, provider.permissionsStore)
    } map { manager =>
      debug(s"Chat Channel Channel manager created: '$domainId/$chatId'")
      this.channelManager = Some(manager)
      manager.state().chatType match {
        case ChatType.Room =>
          this.messageProcessor = Some(new ChatRoomMessageProcessor(
            domainId,
            chatId,
            manager,
            () => this.passivate(),
            message => context.self ! message,
            context))
          // this would only need to happen if a previous instance of this room crashed without
          // cleaning up properly.
          manager.removeAllMembers()
        case ChatType.Channel =>
          context.setReceiveTimeout(120.seconds, ReceiveTimeout(this.domainId, this.chatId))
          manager.state().membership match {
            case ChatMembership.Private =>
              this.messageProcessor = Some(new PrivateChannelMessageProcessor(manager, context))
            case ChatMembership.Public =>
              this.messageProcessor = Some(new PublicChannelMessageProcessor(manager, context))
          }
        case ChatType.Direct =>
          context.setReceiveTimeout(120.seconds, ReceiveTimeout(this.domainId, this.chatId))
          this.messageProcessor = Some(new DirectChatMessageProcessor(manager, context))
      }
      StartUpRequired
    } recoverWith {
      case NonFatal(cause) =>
        error(s"error initializing chat channel: '$domainId/$chatId'", cause)
        Failure(cause)
    }
  }

  def receiveInitialized(msg: Message): Behavior[Message] = {
    msg match {
      case ReceiveTimeout(_, _) =>
        this.onReceiveTimeout()
      case message: RequestMessage =>
        processChatMessage(message)
    }
    Behaviors.same
  }

  override def postStop(): Unit = {
    super.postStop()
    channelManager.foreach { cm =>
      if (cm.state().chatType == ChatType.Room) {
        cm.removeAllMembers()
      }
    }
    DomainPersistenceManagerActor.releasePersistenceProvider(context.self, context.system, domainId)
  }

  private[this] def processChatMessage(message: RequestMessage): Behavior[_ <: Message] = {
    (for {
      messageProcessor <- this.messageProcessor match {
        case Some(mp) => Success(mp)
        case None => Failure(new IllegalStateException("The message processor must be set before processing messages"))
      }
      _ <- messageProcessor.processChatMessage(message) map { result =>
        // FIXME we have a message ordering issue here where the broadcast message will go first to the joining actor.
        result.response foreach (response => response.replyTo ! response.response)
        result.broadcastMessages foreach messageProcessor.broadcast
        ()
      }

    } yield Behaviors.same)
      .recover {
        case cause: ChatNotFoundException =>
          // It seems like there is no reason to stay up, at this point.
          message.replyTo ! RequestFailure(cause)
          this.passivate()

        case cause: Exception =>
          error("Error processing chat message", cause)
          message.replyTo ! RequestFailure(cause)
          this.passivate()
      }.get
  }

  private[this] def onReceiveTimeout(): Behavior[Message] = {
    debug("Receive timeout reached, asking shard region to passivate")
    this.passivate()
  }
}

object ChatActor {
  def apply(shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand]): Behavior[Message] = Behaviors.setup { context =>
    new ChatActor(context, shardRegion, shard)
  }

  def getChatUsernameTopicName(userId: DomainUserId): String = {
    s"chat-user-${userId.userType.toString.toLowerCase}-${userId.username}"
  }

  sealed trait Message extends CborSerializable {
    val domainId: DomainId
    val chatId: String
  }


  private case class ReceiveTimeout(domainId: DomainId, chatId: String) extends Message

  // Incoming Messages

  sealed trait RequestMessage extends Message {
    def replyTo: ActorRef[RequestFailure]
  }

  //
  // RemoveChatRequest
  //
  case class RemoveChatRequest(domainId: DomainId,
                               chatId: String,
                               requester: DomainUserId,
                               replyTo: ActorRef[RemoveChatResponse]) extends RequestMessage

  sealed trait RemoveChatResponse extends CborSerializable


  //
  // JoinChannel
  //
  case class JoinChatRequest(domainId: DomainId,
                             chatId: String,
                             requester: DomainUserSessionId,
                             client: ActorRef[ChatClientActor.OutgoingMessage],
                             replyTo: ActorRef[JoinChatResponse]) extends RequestMessage

  sealed trait JoinChatResponse extends CborSerializable

  case class JoinChatSuccess(info: ChatInfo) extends JoinChatResponse

  //
  // LeaveChannel
  //
  case class LeaveChatRequest(domainId: DomainId,
                              chatId: String,
                              requester: DomainUserSessionId,
                              client: ActorRef[ChatClientActor.OutgoingMessage],
                              replyTo: ActorRef[LeaveChatResponse]) extends RequestMessage

  sealed trait LeaveChatResponse extends CborSerializable

  //
  // AddUserToChannel
  //
  case class AddUserToChatRequest(domainId: DomainId,
                                  chatId: String,
                                  requester: DomainUserSessionId,
                                  userToAdd: DomainUserId,
                                  replyTo: ActorRef[AddUserToChatResponse]) extends RequestMessage

  sealed trait AddUserToChatResponse extends CborSerializable

  //
  // RemoveUserFromChannel
  //
  case class RemoveUserFromChatRequest(domainId: DomainId,
                                       chatId: String,
                                       requester: DomainUserSessionId,
                                       userToRemove: DomainUserId,
                                       replyTo: ActorRef[RemoveUserFromChatResponse]) extends RequestMessage

  sealed trait RemoveUserFromChatResponse extends CborSerializable

  //
  // SetChatName
  //
  case class SetChatNameRequest(domainId: DomainId,
                                chatId: String,
                                requester: DomainUserId,
                                name: String,
                                replyTo: ActorRef[SetChatNameResponse]) extends RequestMessage

  sealed trait SetChatNameResponse extends CborSerializable

  //
  // SetChatTopic
  //
  case class SetChatTopicRequest(domainId: DomainId,
                                 chatId: String,
                                 requester: DomainUserId,
                                 topic: String,
                                 replyTo: ActorRef[SetChatTopicResponse]) extends RequestMessage

  sealed trait SetChatTopicResponse extends CborSerializable

  //
  // MarkChatsEventsSeenRequest
  //
  case class MarkChatsEventsSeenRequest(domainId: DomainId,
                                        chatId: String,
                                        requester: DomainUserSessionId,
                                        eventNumber: Long,
                                        replyTo: ActorRef[MarkChatsEventsSeenResponse]) extends RequestMessage

  sealed trait MarkChatsEventsSeenResponse extends CborSerializable

  //
  // PublishChatMessage
  //
  case class PublishChatMessageRequest(domainId: DomainId,
                                       chatId: String,
                                       requester: DomainUserSessionId,
                                       message: String,
                                       replyTo: ActorRef[PublishChatMessageResponse]) extends RequestMessage

  sealed trait PublishChatMessageResponse extends CborSerializable

  case class PublishChatMessageSuccess(eventNumber: Long, timestamp: Instant) extends PublishChatMessageResponse


  //
  // AddChatPermissions
  //
  case class AddChatPermissionsRequest(domainId: DomainId,
                                       chatId: String,
                                       requester: DomainUserSessionId,
                                       world: Option[Set[String]],
                                       user: Option[Set[UserPermissions]],
                                       group: Option[Set[GroupPermissions]],
                                       replyTo: ActorRef[AddChatPermissionsResponse]) extends RequestMessage

  sealed trait AddChatPermissionsResponse extends CborSerializable

  //
  // RemoveChatPermissions
  //
  case class RemoveChatPermissionsRequest(domainId: DomainId,
                                          chatId: String,
                                          requester: DomainUserSessionId,
                                          world: Option[Set[String]],
                                          user: Option[Set[UserPermissions]],
                                          group: Option[Set[GroupPermissions]],
                                          replyTo: ActorRef[RemoveChatPermissionsResponse]) extends RequestMessage

  sealed trait RemoveChatPermissionsResponse extends CborSerializable

  //
  // SetChatPermissions
  //
  case class SetChatPermissionsRequest(domainId: DomainId,
                                       chatId: String,
                                       requester: DomainUserSessionId,
                                       world: Option[Set[String]],
                                       user: Option[Set[UserPermissions]],
                                       group: Option[Set[GroupPermissions]],
                                       replyTo: ActorRef[SetChatPermissionsResponse]) extends RequestMessage

  sealed trait SetChatPermissionsResponse extends CborSerializable

  //
  // GetClientChatPermissions
  //
  case class GetClientChatPermissionsRequest(domainId: DomainId,
                                             chatId: String,
                                             requester: DomainUserSessionId,
                                             replyTo: ActorRef[GetClientChatPermissionsResponse]) extends RequestMessage

  sealed trait GetClientChatPermissionsResponse extends CborSerializable

  case class GetClientChatPermissionsSuccess(permissions: Set[String]) extends GetClientChatPermissionsResponse

  //
  // GetWorldChatPermissions
  //
  case class GetWorldChatPermissionsRequest(domainId: DomainId,
                                            chatId: String,
                                            requester: DomainUserSessionId,
                                            replyTo: ActorRef[GetWorldChatPermissionsResponse]) extends RequestMessage

  sealed trait GetWorldChatPermissionsResponse extends CborSerializable

  case class GetWorldChatPermissionsSuccess(permissions: Set[String]) extends GetWorldChatPermissionsResponse

  //
  // GetAllUserChatPermissions
  //
  case class GetAllUserChatPermissionsRequest(domainId: DomainId,
                                              chatId: String,
                                              requester: DomainUserSessionId,
                                              replyTo: ActorRef[GetAllUserChatPermissionsResponse]) extends RequestMessage

  sealed trait GetAllUserChatPermissionsResponse extends CborSerializable

  case class GetAllUserChatPermissionsSuccess(users: Map[DomainUserId, Set[String]]) extends GetAllUserChatPermissionsResponse

  //
  // GetAllGroupChatPermissions
  //
  case class GetAllGroupChatPermissionsRequest(domainId: DomainId,
                                               chatId: String,
                                               requester: DomainUserSessionId,
                                               replyTo: ActorRef[GetAllGroupChatPermissionsResponse]) extends RequestMessage

  sealed trait GetAllGroupChatPermissionsResponse extends CborSerializable

  case class GetAllGroupChatPermissionsSuccess(groups: Map[String, Set[String]]) extends GetAllGroupChatPermissionsResponse

  //
  // GetUserChatPermissions
  //
  case class GetUserChatPermissionsRequest(domainId: DomainId,
                                           chatId: String,
                                           requester: DomainUserSessionId,
                                           userId: DomainUserId,
                                           replyTo: ActorRef[GetUserChatPermissionsResponse]) extends RequestMessage

  sealed trait GetUserChatPermissionsResponse extends CborSerializable

  case class GetUserChatPermissionsSuccess(permissions: Set[String]) extends GetUserChatPermissionsResponse

  //
  // GetGroupChatPermissions
  //
  case class GetGroupChatPermissionsRequest(domainId: DomainId,
                                            chatId: String,
                                            requester: DomainUserSessionId,
                                            groupId: String,
                                            replyTo: ActorRef[GetGroupChatPermissionsResponse]) extends RequestMessage

  sealed trait GetGroupChatPermissionsResponse extends CborSerializable

  case class GetGroupChatPermissionsSuccess(permissions: Set[String]) extends GetGroupChatPermissionsResponse

  //
  // GetChatHistory
  //
  case class GetChatHistoryRequest(domainId: DomainId,
                                   chatId: String,
                                   requester: Option[DomainUserSessionId],
                                   offset: Option[Long],
                                   limit: Option[Long],
                                   startEvent: Option[Long],
                                   forward: Option[Boolean],
                                   eventTypes: Option[Set[String]],
                                   messageFilter: Option[String] = None,
                                   replyTo: ActorRef[GetChatHistoryResponse]) extends RequestMessage

  sealed trait GetChatHistoryResponse extends CborSerializable

  case class GetChatHistorySuccess(events: PagedData[ChatEvent]) extends GetChatHistoryResponse


  //
  // Generic Responses
  //

  case class RequestSuccess() extends CborSerializable
    with RemoveChatResponse
    with SetChatNameResponse
    with SetChatTopicResponse
    with LeaveChatResponse
    with AddUserToChatResponse
    with RemoveUserFromChatResponse
    with MarkChatsEventsSeenResponse
    with AddChatPermissionsResponse
    with RemoveChatPermissionsResponse
    with SetChatPermissionsResponse

  case class RequestFailure(cause: Throwable) extends CborSerializable
    with RemoveChatResponse
    with JoinChatResponse
    with LeaveChatResponse
    with AddUserToChatResponse
    with RemoveUserFromChatResponse
    with SetChatNameResponse
    with SetChatTopicResponse
    with MarkChatsEventsSeenResponse
    with PublishChatMessageResponse
    with AddChatPermissionsResponse
    with RemoveChatPermissionsResponse
    with SetChatPermissionsResponse
    with GetClientChatPermissionsResponse
    with GetWorldChatPermissionsResponse
    with GetAllUserChatPermissionsResponse
    with GetAllGroupChatPermissionsResponse
    with GetUserChatPermissionsResponse
    with GetGroupChatPermissionsResponse
    with GetChatHistoryResponse


  // Exceptions
  sealed abstract class ChatException(message: String) extends Exception(message)

  case class ChatNotJoinedException(chatId: String) extends ChatException(s"Can not perform this action on a chat that is not joined")

  case class ChatAlreadyJoinedException(chatId: String) extends ChatException("")

  case class ChatNotFoundException(chatId: String) extends ChatException("")

  case class ChatAlreadyExistsException(chatId: String) extends ChatException("")

  case class InvalidChatMessageException(message: String) extends ChatException(message)

}


