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
import scala.util.control.NonFatal
import akka.actor.ActorRef

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

  //  override def persistenceId: String = "ChatChannel-" + self.path.name

  val mediator = DistributedPubSub(context.system).mediator

  // Here None signifies that the channel does not exist.
  var channelManager: Option[ChatChannelManager] = None

  // Used for rooms only
  var connectedClients: Set[ActorRef] = Set()
  var connectedUserCount: Map[String, Int] = Map()

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
      ChatChannelManager.create(channelId, provider.chatChannelStore)
    } map { manager =>
      log.debug(s"Chat Channel Channel manager created: '${domainFqn}/${channelId}'")
      this.channelManager = Some(manager)
      if (manager.isRoom) {
        context.become(receiveRoomMessages)
      } else {
        // TODO: Load from configuration 
        context.setReceiveTimeout(120.seconds)
        context.become(receiveChannelMessages)
      }
      ()
    } recoverWith {
      case NonFatal(cause) =>
        log.debug(s"error initializing chat channel: '${domainFqn}/${channelId}'")
        Failure(cause)
    }
  }

  def receiveRoomMessages: Receive = {
    case message: ExistingChannelMessage =>
      processRoomMessage(message)
        .recover { case cause: Exception => this.unexpectedError(cause) }
    case other: Any =>
      this.receiveCommon(other)
  }

  def processRoomMessage(message: ExistingChannelMessage): Try[Unit] = {
    message match {
      case message: JoinChannelRequest =>
        if (connectedClients.contains(sender)) {
          // FIXME: Handler session already joined error
          ???
        } else {
          connectedClients += sender
          if (connectedUserCount.contains(message.username)) {
            connectedUserCount += message.username -> (connectedUserCount(message.username) + 1)
            ???
          } else {
            connectedUserCount += message.username -> 1
            sendRoomMessageToManager(message)
          }
        }
      case message: LeaveChannelRequest =>
        if (connectedClients.contains(sender)) {
          connectedClients -= sender
          connectedUserCount.get(message.username) match {
            case Some(count) if (count == 1) =>
              connectedUserCount -= message.username
              sendRoomMessageToManager(message)
            case Some(count) =>
              connectedUserCount += message.username -> (connectedUserCount(message.username) - 1)
              ???
            case None =>
              // FIXME: handle error
              ???
          }
        } else {
          // FIXME: Handler session not joined error
          ???
        }
      case message: AddUserToChannelRequest      => ??? // FIXME: Is this supported or do we just throw an error
      case message: RemoveUserFromChannelRequest => ??? // FIXME: Is this supported or do we just throw an error
      case _                                     => sendRoomMessageToManager(message)
    }
  }

  def sendRoomMessageToManager(message: ExistingChannelMessage): Try[Unit] = {
    this.channelManager match {
      case Some(manager) =>
        manager.handleChatMessage(message) map { result =>
          result.state foreach (updateState(_))
          result.response foreach (response => sender ! response)
          result.broadcastMessages foreach (broadcastToRoom(_))
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

  def receiveChannelMessages: Receive = {
    case message: ExistingChannelMessage =>
      processChannelMessage(message)
        .recover { case cause: Exception => this.unexpectedError(cause) }
    case other: Any =>
      this.receiveCommon(other)
  }

  def processChannelMessage(message: ExistingChannelMessage): Try[Unit] = {
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

  private[this] def broadcastToRoom(message: Any): Unit = {
    connectedClients.foreach(client => {
      // FIXME: Send message to client 
    })
  }

  private[this] def updateState(state: ChatChannelState): Unit = {
    // FIXME TODO
  }
}
