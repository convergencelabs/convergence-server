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
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChatChannelException
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ExistingChannelMessage
import com.convergencelabs.server.domain.chat.ChatChannelMessages.JoinChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.LeaveChannelRequest

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorLogging
import akka.actor.ReceiveTimeout
import akka.actor.Status
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.convergencelabs.server.domain.chat.ChatChannelMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoveUserFromChannelRequest

object ChatChannelActor {

  def getChatUsernameTopicName(username: String): String = {
    return s"chat-user-${username}"
  }

  case object Stop
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
  members: Set[String])

class ChatChannelActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {
  import ChatChannelActor._
  import ChatChannelMessages._
  import akka.cluster.sharding.ShardRegion.Passivate

  log.debug(s"Chat Channel Actor starting in domain: '${domainFqn}'")

  // Here None signifies that the channel does not exist.
  var channelManager: Option[ChatChannelStateManager] = None
  var messageHelper: Option[ChatMessagingHelper] = None

  // Default recieve will be called the first time
  override def receive: Receive = {
    case message: ExistingChannelMessage =>
      initialize(message.channelId)
        .flatMap { _ =>
          log.debug(s"Chat Channel Actor initialized processing message: '${domainFqn}/${message.channelId}'")
          processChannelMessage(message)
        }
        .recover { case cause: Exception => this.unexpectedError(cause) }
    case other: Any =>
      this.receiveCommon(other)
  }

  private[this] def initialize(channelId: String): Try[Unit] = {
    log.debug(s"Chat Channel Actor initializing: '${domainFqn}/${channelId}'")
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) flatMap { provider =>
      log.debug(s"Chat Channel aquired persistence, creating channel manager: '${domainFqn}/${channelId}'")
      ChatChannelStateManager.create(channelId, provider.chatChannelStore)
    } map { manager =>
      log.debug(s"Chat Channel Channel manager created: '${domainFqn}/${channelId}'")
      this.channelManager = Some(manager)
      manager.state().channelType match {
        case "room" =>
          this.messageHelper = Some(new ChatRoomMessagingHelper(manager, context))
          // this would only need to happen if a previous instance of this room crashed without 
          // cleaning up properly.
          manager.removeAllMembers()
        case "group" =>
          context.setReceiveTimeout(120.seconds)
          this.messageHelper = Some(new GroupChannelMessagingHelper(manager, context))
        case "direct" =>
          context.setReceiveTimeout(120.seconds)
          this.messageHelper = Some(new DirectChannelMessagingHelper(manager, context))
      }

      context.become(receiveWhenInitiazlied)
      ()
    } recoverWith {
      case NonFatal(cause) =>
        log.debug(s"error initializing chat channel: '${domainFqn}/${channelId}'")
        Failure(cause)
    }
  }

  def receiveWhenInitiazlied: Receive = {
    case message: ExistingChannelMessage =>
      processChannelMessage(message)
        .recover { case cause: Exception => this.unexpectedError(cause) }
    case other: Any =>
      this.receiveCommon(other)
  }

  private[this] def processChannelMessage(message: ExistingChannelMessage): Try[Unit] = {
    (for {
      (cm, mh) <- getHelpers()
      _ <- mh.validateMessage(message)
      _ <- mh.preProcessMessage(message) match {
        case Left(message) =>
          cm.handleChatMessage(message) map { result =>
            result.response foreach (response => sender ! response)
            result.broadcastMessages foreach (mh.boradcast(_))
            ()
          }
        case Right(response) =>
          sender ! response
          Success(())
      }
    } yield {
    }).recover {
      case cause: ChannelNotFoundException =>
        // It seems like there is no reason to stay up, at this point.
        context.parent ! Passivate(stopMessage = Stop)
        sender ! Status.Failure(cause)

      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def getHelpers(): Try[(ChatChannelStateManager, ChatMessagingHelper)] = {
    (this.channelManager, this.messageHelper) match {
      case (Some(cm), Some(mh)) => Success((cm, mh))
      case _ => Failure(new IllegalStateException("Message Helper and Channel Manager must be set to process messages"))
    }
  }

  private[this] def receiveCommon: PartialFunction[Any, Unit] = {
    case ReceiveTimeout =>
      this.onReceiveTimeout()
    case Stop =>
      onStop()
    case unhandled: Any =>
      this.unhandled(unhandled)
  }

  private[this] def onReceiveTimeout(): Unit = {
    log.debug("Receive timeout reached, asking shard region to passivate")
    context.parent ! Passivate(stopMessage = Stop)
  }

  private[this] def onStop(): Unit = {
    log.debug("Receive stop signal shutting down")
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
    channelManager.foreach { cm =>
      if (cm.state().channelType == "room") {
        cm.removeAllMembers()
      }
    }
    context.stop(self)
  }

  private[this] def unexpectedError(cause: Exception): Unit = {
    cause match {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }
}

