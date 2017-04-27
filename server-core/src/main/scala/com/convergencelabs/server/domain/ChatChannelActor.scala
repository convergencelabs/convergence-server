package com.convergencelabs.server.domain

import java.time.Instant

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ReceiveTimeout
import akka.actor.Status
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish

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

  // TODO: Load from configuration 
  context.setReceiveTimeout(120.seconds)

  //  override def persistenceId: String = "ChatChannel-" + self.path.name

  val mediator = DistributedPubSub(context.system).mediator

  // Here None signifies that the channel does not exist.
  var channelManager: Option[ChatChannelManager] = None

  //  override def receiveRecover: Receive = {
  //    case state: ChatChannelActorState =>
  //      channelActorState = state
  //      state.status match {
  //        case ActorStatus.Initialized => context.become(receiveWhenInitialized)
  //      }
  //  }

  // Default recieve will be called the first time
  //  override def receiveCommand: Receive = {
  override def receive: Receive = {
    case message: ExistingChannelMessage =>
      initialize(message.channelId)
        .flatMap { _ =>
          log.debug(s"Chat Channel Actor initialized processing message: '${domainFqn}/${message.channelId}'")
          processChatMessage(message)
        }
        .recover { case cause: Exception => this.unexpectedError(cause) }
    case other: Any =>
      this.receiveCommon(other)
  }

  private[this] def initialize(channelId: String): Try[Unit] = {
    log.debug(s"Chat Channel Actor starting: '${domainFqn}/${channelId}'")
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) flatMap { provider =>
      ChatChannelManager.create(channelId, provider.chatChannelStore) map { manager =>
        this.channelManager = Some(manager)
        context.become(receiveWhenInitialized)
      }
    }
  }

  def receiveWhenInitialized: Receive = {
    case message: ExistingChannelMessage =>
      processChatMessage(message)
        .recover { case cause: Exception => this.unexpectedError(cause) }
    case other: Any =>
      this.receiveCommon(other)
  }

  def processChatMessage(message: ExistingChannelMessage): Try[Unit] = {
    this.channelManager match {
      case Some(manager) =>
        manager.handleChatMessage(message) map { result =>
          result.state foreach (updateState(_))
          result.response foreach (response => sender ! response)
          result.broadcastMessages foreach (broadcastToChannel(_))
        } recover {
          case cause: ChannelNotFoundException =>
            // It seems like there is no reason to stay up, at this point.
            context.parent ! Passivate(stopMessage = Stop)
            sender ! Status.Failure(cause)

          case ChatChannelException(cause) =>
            sender ! Status.Failure(cause)
        }
      case None =>
        Failure(new IllegalStateException("Can't process chat message with no chat channel manager set."))
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
    context.stop(self)
  }

  private[this] def unexpectedError(cause: Exception): Unit = {
    cause match {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  private[this] def broadcastToChannel(message: Any): Unit = {
    this.channelManager match {
      case Some(mgr) =>
        val members = mgr.state().members
        members.foreach { member =>
          val topic = ChatChannelActor.getChatUsernameTopicName(member)
          mediator ! Publish(topic, message)
        }
      case None =>
        // FIXME Explode? We are corrupted somehow?
    }

  }

  private[this] def updateState(state: ChatChannelState): Unit = {
    // FIXME TODO
  }
}
