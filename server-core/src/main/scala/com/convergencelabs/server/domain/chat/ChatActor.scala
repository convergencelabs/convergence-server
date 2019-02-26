package com.convergencelabs.server.domain.chat

import java.time.Instant

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

import com.convergencelabs.server.actor.ShardedActor
import com.convergencelabs.server.actor.ShardedActorStatUpPlan
import com.convergencelabs.server.actor.StartUpRequired
import com.convergencelabs.server.datastore.domain.ChatMember
import com.convergencelabs.server.datastore.domain.ChatMembership
import com.convergencelabs.server.datastore.domain.ChatType
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.chat.ChatMessages.ChatNotFoundException
import com.convergencelabs.server.domain.chat.ChatMessages.ExistingChatMessage

import akka.actor.ReceiveTimeout
import akka.actor.Status

object ChatActor {

  def getChatUsernameTopicName(userId: DomainUserId): String = {
    return s"chat-user-${userId.userType.toString.toLowerCase}-${userId.username}"
  }
}

case class ChatChannelState(
  id: String,
  chatType: ChatType.Value,
  created: Instant,
  membership: ChatMembership.Value,
  name: String,
  topic: String,
  lastEventTime: Instant,
  lastEventNumber: Long,
  members: Map[DomainUserId, ChatMember])

class ChatActor private[domain] () extends ShardedActor(classOf[ExistingChatMessage]) {
  import ChatActor._
  import ChatMessages._

  var domainFqn: DomainFqn = _
  var channelId: String = _

  // Here None signifies that the channel does not exist.
  var channelManager: Option[ChatStateManager] = None
  var messageProcessor: Option[ChatMessageProcessor] = None

  protected def setIdentityData(message: ExistingChatMessage): Try[String] = {
    this.domainFqn = message.domainFqn
    this.channelId = message.chatId
    Success(s"${domainFqn.namespace}/${domainFqn.domainId}/${this.channelId}")
  }

  protected def initialize(message: ExistingChatMessage): Try[ShardedActorStatUpPlan] = {
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) flatMap { provider =>
      log.debug(s"Chat Channel aquired persistence, creating channel manager: '${domainFqn}/${channelId}'")
      ChatStateManager.create(channelId, provider.chatStore, provider.permissionsStore)
    } map { manager =>
      log.debug(s"Chat Channel Channel manager created: '${domainFqn}/${channelId}'")
      this.channelManager = Some(manager)
      manager.state().chatType match {
        case ChatType.Room =>
          this.messageProcessor = Some(new ChatRoomMessageProcessor(domainFqn, channelId, manager, () => this.passivate(), context))
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
        log.error(cause, s"error initializing chat channel: '${domainFqn}/${channelId}'")
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

  private[this] def processChannelMessage(message: ExistingChatMessage): Try[Unit] = {
    (for {
      messageProcessor <- this.messageProcessor match {
        case Some(mp) => Success(mp)
        case None => Failure(new IllegalStateException("The message processor must be set before processing messages"))
      }
      _ <- messageProcessor.processChatMessage(message) map { result =>
        // FIXME we have a message ordering issue here where the broadcast message will go first to the joining actor.
        result.response foreach (response => sender ! response)
        result.broadcastMessages foreach (messageProcessor.boradcast(_))
        ()
      }
    } yield (())).recover {
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

  override def postStop(): Unit = {
    super.postStop()
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
    channelManager.foreach { cm =>
      if (cm.state().chatType == ChatType.Room) {
        cm.removeAllMembers()
      }
    }
  }

  private[this] def unexpectedError(cause: Exception): Unit = {
    cause match {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }
}

