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

import akka.actor.{ReceiveTimeout, Status}
import com.convergencelabs.convergence.server.actor.{ShardedActor, ShardedActorStatUpPlan, StartUpRequired}
import com.convergencelabs.convergence.server.datastore.domain.{ChatMember, ChatMembership, ChatType, DomainPersistenceManagerActor}
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.{ChatNotFoundException, ExistingChatMessage}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId}

import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
 * The [[ChatActor]] represents a single unique chat instance in the system. It
 * is sharded across the backend nodes and can represent a chat channel or a
 * chat room. The handling of messages is delegated to a ChatMessageProcessor
 * which implements the specific business logic of each type of chat.
 */
class ChatActor private[domain]() extends ShardedActor(classOf[ExistingChatMessage]) {

  private[this] var domainId: DomainId = _
  private[this] var channelId: String = _

  // Here None signifies that the channel does not exist.
  private[this] var channelManager: Option[ChatStateManager] = None
  private[this] var messageProcessor: Option[ChatMessageProcessor] = None

  protected def setIdentityData(message: ExistingChatMessage): Try[String] = {
    this.domainId = message.domainId
    this.channelId = message.chatId
    Success(s"${domainId.namespace}/${domainId.domainId}/${this.channelId}")
  }

  protected def initialize(message: ExistingChatMessage): Try[ShardedActorStatUpPlan] = {
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainId) flatMap { provider =>
      log.debug(s"Chat Channel acquired persistence, creating channel manager: '$domainId/$channelId'")
      ChatStateManager.create(channelId, provider.chatStore, provider.permissionsStore)
    } map { manager =>
      log.debug(s"Chat Channel Channel manager created: '$domainId/$channelId'")
      this.channelManager = Some(manager)
      manager.state().chatType match {
        case ChatType.Room =>
          this.messageProcessor = Some(new ChatRoomMessageProcessor(
            domainId,
            channelId,
            manager,
            () => this.passivate(),
            message => self ! message,
            context))
          // this would only need to happen if a previous instance of this room crashed without
          // cleaning up properly.
          manager.removeAllMembers()
        case ChatType.Channel =>
          context.setReceiveTimeout(120.seconds)
          manager.state().membership match {
            case ChatMembership.Private =>
              this.messageProcessor = Some(new PrivateChannelMessageProcessor(manager, context))
            case ChatMembership.Public =>
              this.messageProcessor = Some(new PublicChannelMessageProcessor(manager, context))
          }
        case ChatType.Direct =>
          context.setReceiveTimeout(120.seconds)
          this.messageProcessor = Some(new DirectChatMessageProcessor(manager, context))
      }
      StartUpRequired
    } recoverWith {
      case NonFatal(cause) =>
        log.error(cause, s"error initializing chat channel: '$domainId/$channelId'")
        Failure(cause)
    }
  }

  def receiveInitialized: Receive = {
    case message: ExistingChatMessage =>
      processChannelMessage(message)
        .recover { case cause: Exception => this.unexpectedError(cause) }
    case ReceiveTimeout =>
      this.onReceiveTimeout()
    case unhandled: Any =>
      this.unhandled(unhandled)
  }

  override def postStop(): Unit = {
    super.postStop()
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainId)
    channelManager.foreach { cm =>
      if (cm.state().chatType == ChatType.Room) {
        cm.removeAllMembers()
      }
    }
  }

  private[this] def processChannelMessage(message: ExistingChatMessage): Try[Unit] = {
    (for {
      messageProcessor <- this.messageProcessor match {
        case Some(mp) => Success(mp)
        case None => Failure(new IllegalStateException("The message processor must be set before processing messages"))
      }
      _ <- messageProcessor.processChatMessage(message) map { result =>
        // FIXME we have a message ordering issue here where the broadcast message will go first to the joining actor.
        result.response foreach (response => sender ! response)
        result.broadcastMessages foreach messageProcessor.broadcast
        ()
      }
    } yield ()).recover {
      case cause: ChatNotFoundException =>
        // It seems like there is no reason to stay up, at this point.
        this.passivate()
        sender ! Status.Failure(cause)

      case cause: Exception =>
        log.error(cause, "Error processing chat message")
        sender ! Status.Failure(cause)
    }
  }

  private[this] def onReceiveTimeout(): Unit = {
    log.debug("Receive timeout reached, asking shard region to passivate")
    this.passivate()
  }

  private[this] def unexpectedError(cause: Exception): Unit = {
    cause match {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }
}

object ChatActor {
  def getChatUsernameTopicName(userId: DomainUserId): String = {
    s"chat-user-${userId.userType.toString.toLowerCase}-${userId.username}"
  }
}

case class ChatChannelState(id: String,
                            chatType: ChatType.Value,
                            created: Instant,
                            membership: ChatMembership.Value,
                            name: String,
                            topic: String,
                            lastEventTime: Instant,
                            lastEventNumber: Long,
                            members: Map[DomainUserId, ChatMember])
