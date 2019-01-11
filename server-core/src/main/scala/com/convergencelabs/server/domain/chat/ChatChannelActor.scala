package com.convergencelabs.server.domain.chat

import java.time.Instant

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelNotFoundException
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ExistingChannelMessage

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ReceiveTimeout
import akka.actor.Status
import com.convergencelabs.server.datastore.domain.ChatChannelMember
import com.convergencelabs.server.actor.ShardedActor
import com.convergencelabs.server.actor.StartUpRequired
import com.convergencelabs.server.actor.ShardedActorStatUpPlan

object ChatChannelActor {

  def getChatUsernameTopicName(username: String): String = {
    return s"chat-user-${username}"
  }
}

case class ChatChannelState(
  id: String,
  channelType: String, // make enum?
  created: Instant,
  isPrivate: Boolean,
  name: String,
  topic: String,
  lastEventTime: Instant,
  lastEventNumber: Long,
  members: Map[String, ChatChannelMember])

class ChatChannelActor private[domain] () extends ShardedActor(classOf[ExistingChannelMessage]) {
  import ChatChannelActor._
  import ChatChannelMessages._

  var domainFqn: DomainFqn = _
  var channelId: String = _

  // Here None signifies that the channel does not exist.
  var channelManager: Option[ChatChannelStateManager] = None
  var messageProcessor: Option[ChatChannelMessageProcessor] = None

  protected def setIdentityData(message: ExistingChannelMessage): Try[String] = {
    this.domainFqn = message.domainFqn
    this.channelId = message.channelId
    Success(s"${domainFqn.namespace}/${domainFqn.domainId}/${this.channelId}")
  }

  protected def initialize(message: ExistingChannelMessage): Try[ShardedActorStatUpPlan] = {
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) flatMap { provider =>
      log.debug(s"Chat Channel aquired persistence, creating channel manager: '${domainFqn}/${channelId}'")
      ChatChannelStateManager.create(channelId, provider.chatChannelStore, provider.permissionsStore)
    } map { manager =>
      log.debug(s"Chat Channel Channel manager created: '${domainFqn}/${channelId}'")
      this.channelManager = Some(manager)
      manager.state().channelType match {
        case "room" =>
          this.messageProcessor = Some(new ChatRoomMessageProcessor(domainFqn, channelId, manager, () => this.passivate(), context))
          // this would only need to happen if a previous instance of this room crashed without
          // cleaning up properly.
          manager.removeAllMembers()
        case "group" =>
          context.setReceiveTimeout(120.seconds)
          if (manager.state().isPrivate) {
            this.messageProcessor = Some(new PrivateChannelMessageProcessor(manager, context))
          } else {
            this.messageProcessor = Some(new PublicChannelMessageProcessor(manager, context))
          }
        case "direct" =>
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
    case message: ExistingChannelMessage =>
      processChannelMessage(message)
        .recover { case cause: Exception => this.unexpectedError(cause) }
    case ReceiveTimeout =>
      this.onReceiveTimeout()
    case unhandled: Any =>
      this.unhandled(unhandled)
  }

  private[this] def processChannelMessage(message: ExistingChannelMessage): Try[Unit] = {
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
      case cause: ChannelNotFoundException =>
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
      if (cm.state().channelType == "room") {
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

