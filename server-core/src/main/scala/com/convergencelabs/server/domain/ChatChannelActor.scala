package com.convergencelabs.server.domain

import java.time.Instant

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.model.SessionKey

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ReceiveTimeout
import akka.actor.Status
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish

object ChatChannelActor {

  object ActorStatus extends Enumeration {
    val Initialized, NotInitialized = Value
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

  var persistence: DomainPersistenceProvider = _

  case class ChatChannelActorState(status: ActorStatus.Value, state: Option[ChatChannelState])

  sealed trait ChatChannelMessage {
    val channelId: String
  }

  sealed trait ExistingChannelMessage extends ChatChannelMessage

  case object Stop

  // Incoming Messages
  case class CreateChannelRequest(channelId: String, channelType: String,
    channelMembership: String, name: Option[String], topic: Option[String],
    members: List[String]) extends ChatChannelMessage
  case class CreateChannelResponse(channelId: String)

  case class RemoveChannelRequest(channelId: String, username: String) extends ExistingChannelMessage

  case class JoinChannelRequest(channelId: String, username: String) extends ExistingChannelMessage
  case class LeaveChannelRequest(channelId: String, username: String) extends ExistingChannelMessage
  case class AddUserToChannelRequest(channelId: String, username: String, addedBy: String) extends ExistingChannelMessage
  case class RemoveUserFromChannelRequest(channelId: String, username: String, removedBy: String) extends ExistingChannelMessage

  case class SetChannelNameRequest(channelId: String, name: String, setBy: String) extends ExistingChannelMessage
  case class SetChannelTopicRequest(channelId: String, topic: String, setBy: String) extends ExistingChannelMessage
  case class MarkChannelEventsSeenRequest(channelId: String, eventNumber: Long, username: String) extends ExistingChannelMessage

  case class PublishChatMessageRequest(channelId: String, sk: SessionKey, message: String) extends ExistingChannelMessage

  case class ChannelHistoryRequest(channelId: String, username: String, limit: Option[Int], offset: Option[Int],
    forward: Option[Boolean], events: List[String]) extends ExistingChannelMessage
  case class ChannelHistoryResponse(events: List[ChatChannelEvent])

  // Outgoing Broadcast Messages 
  case class UserJoinedChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String)
  case class UserLeftChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String)
  case class UserAddedToChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String, addedBy: String)
  case class UserRemovedFromChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String, removedBy: String)

  case class ChannelJoined(channelId: String, username: String)
  case class ChannelLeft(channelId: String, username: String)
  case class ChannelRemoved(channelId: String)

  case class RemoteChatMessage(channelId: String, eventNumber: Long, timestamp: Instant, sk: SessionKey, message: String)

  // Exceptions
  case class ChannelNotJoinedException(channelId: String) extends Exception()
  case class ChannelAlreadyJoinedException(channelId: String) extends Exception()
  case class ChannelNotFoundException(channelId: String) extends Exception()
  case class ChannelAlreadyExistsException(channelId: String) extends Exception()

  object ChatChannelException {
    def apply(t: Throwable): Boolean = t match {
      case _: ChannelNotJoinedException | _: ChannelAlreadyJoinedException | _: ChannelNotFoundException | _: ChannelAlreadyExistsException => true
      case _ => false
    }
    def unapply(t: Throwable): Option[Throwable] = if (apply(t)) Some(t) else None
  }

  def getChatUsernameTopicName(username: String): String = {
    return s"chat-user-${username}"
  }
}

class ChatChannelActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {
  import ChatChannelActor._
  import akka.cluster.sharding.ShardRegion.Passivate

  log.debug(s"Chat Channel Actor starting in domain: '${domainFqn}'")

  // TODO: Load from configuration 
  context.setReceiveTimeout(120.seconds)

  //  override def persistenceId: String = "ChatChannel-" + self.path.name

  val mediator = DistributedPubSub(context.system).mediator

  // Here None signifies that the channel does not exist.
  var channelActorState: ChatChannelActorState = ChatChannelActorState(ActorStatus.NotInitialized, None)
  var channelId: String = _

  // FIXME what is the point of this method?
  def updateState(state: ChatChannelActorState): Unit = channelActorState = state

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
    case message: ChatChannelMessage =>
      initialize(message.channelId)
        .flatMap { _ =>
          log.debug(s"Chat Channel Actor initialized processing message: '${domainFqn}/${message.channelId}'")
          handleChatMessage(message)
        }
        .recover { case cause: Exception => this.unexpectedError(message, cause) }
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

  private[this] def initialize(channelId: String): Try[Unit] = {
    this.channelId = channelId
    log.debug(s"Chat Channel Actor starting: '${domainFqn}/${channelId}'")
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) flatMap { provider =>
      persistence = provider
      // FIXME we probably want a get channel optional...
      // FIXME should we get a method that returns everyting below?

      provider.chatChannelStore.getChatChannel(channelId) map { channel =>
        // FIXME don't have members?
        val members = Set("michael", "cameron")
        // FIXME don't have the sequence number?
        val maxEvent = 7L
        // FIXME don't have the last event time
        val lastTime = Instant.now()

        this.channelActorState = ChatChannelActorState(ActorStatus.Initialized, Some(
          ChatChannelState(
            channelId,
            channel.channelType,
            channel.created,
            channel.isPrivate,
            channel.name,
            channel.topic,
            lastTime,
            maxEvent,
            members)))
        //    persist(channelActorState)(updateState)
        ()
      } recover {
        case cause: EntityNotFoundException =>
          this.channelActorState = ChatChannelActorState(ActorStatus.Initialized, None)
      } map { _ =>
        context.become(receiveWhenInitialized)
      }
    }
  }

  def receiveWhenInitialized: Receive = {
    case message: ChatChannelMessage =>
      handleChatMessage(message)
        .recover { case cause: Exception => this.unexpectedError(message, cause) }
    case ReceiveTimeout =>
      this.onReceiveTimeout()
    case Stop =>
      onStop()
    case unhandled: Any =>
      this.unhandled(unhandled)
  }

  def handleChatMessage(message: ChatChannelMessage): Try[Unit] = {
    (message match {
      case message: CreateChannelRequest =>
        onCreateChannel(message)
      case other: ExistingChannelMessage =>
        assertChannelExists() flatMap { state =>
          other match {
            case message: RemoveChannelRequest =>
              onRemoveChannel(message)
            case message: JoinChannelRequest =>
              onJoinChannel(message, state)
            case message: LeaveChannelRequest =>
              onLeaveChannel(message)
            case message: AddUserToChannelRequest =>
              onAddUserToChannel(message)
            case message: RemoveUserFromChannelRequest =>
              onRemoveUserFromChannel(message)
            case message: SetChannelNameRequest =>
              onSetChatChannelName(message)
            case message: SetChannelTopicRequest =>
              onSetChatChannelTopic(message)
            case message: MarkChannelEventsSeenRequest =>
              onMarkEventsSeen(message)
            case message: ChannelHistoryRequest =>
              onGetHistory(message)
            case message: PublishChatMessageRequest =>
              onPublishMessage(message)
          }
        }
    }) map { message => 
      sender ! message
    } recover {
      case ChatChannelException(cause) =>
        sender ! Status.Failure(cause)
    }
  }

  def onCreateChannel(message: CreateChannelRequest): Try[Unit] = {
    val CreateChannelRequest(channelId, channelType, channelMembership, name, topic, members) = message;
    ???
  }

  def onRemoveChannel(message: RemoveChannelRequest): Try[Unit] = {
    val RemoveChannelRequest(channelId, username) = message;
    ???
  }

  def onJoinChannel(message: JoinChannelRequest, state: ChatChannelState): Try[Unit] = {
    val JoinChannelRequest(channelId, username) = message;
    val members = state.members
    if (members contains username) {
      Failure(ChannelAlreadyJoinedException(channelId))
    } else {
      val newMembers = members + username
      // update the database, potentially, we could do this async.
      updateState(state.copy(members = newMembers))
      Success(())
    }
  }

  def onLeaveChannel(message: LeaveChannelRequest): Try[Unit] = {
    val LeaveChannelRequest(channelId, username) = message;
    ???
  }

  def onAddUserToChannel(message: AddUserToChannelRequest): Try[Unit] = {
    val AddUserToChannelRequest(channelId, username, addedBy) = message;
    ???
  }

  def onRemoveUserFromChannel(message: RemoveUserFromChannelRequest): Try[Unit] = {
    val RemoveUserFromChannelRequest(channelId, username, removedBy) = message;
    ???
  }

  def onSetChatChannelName(message: SetChannelNameRequest): Try[Unit] = {
    val SetChannelNameRequest(channelId, name, setBy) = message;
    ???
  }

  def onSetChatChannelTopic(message: SetChannelTopicRequest): Try[Unit] = {
    val SetChannelTopicRequest(channelId, topic, setBy) = message;
    ???
  }

  def onMarkEventsSeen(message: MarkChannelEventsSeenRequest): Try[Unit] = {
    val MarkChannelEventsSeenRequest(channelId, eventNumber, username) = message;
    ???
  }

  def onGetHistory(message: ChannelHistoryRequest): Try[Unit] = {
    val ChannelHistoryRequest(username, channleId, limit, offset, forward, events) = message;
    ???
  }

  def onPublishMessage(message: PublishChatMessageRequest): Try[Unit] = {
    val PublishChatMessageRequest(sk, channeId, msg) = message;
    ???
  }

  def unexpectedError(message: ChatChannelMessage, cause: Exception): Unit = {
    // this can only be an initialization excpetion, so we need to
    // reply back that something bad happened. I suppose in reality
    // we would want to know if this was a request message, if not then
    // perhaps we don't need to reply. We probably can refine the sealed
    // traits above to let us know if we need to do this or not.
    cause match {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  private[this] def broadcastToChannel(channelId: String, message: AnyRef) {
    val members = getChatChannelMembers(channelId)
    members.foreach { member =>
      val topic = getChatUsernameTopicName(member)
      mediator ! Publish(topic, message)
    }
  }

  private def assertChannelExists(): Try[ChatChannelState] = {
    this.channelActorState.state match {
      case Some(state) => Success(state)
      case None => Failure(ChannelNotFoundException(channelId))
    }
  }

  private[this] def getChatChannelMembers(channelId: String): List[String] = {
    ???
  }

  private[this] def updateState(state: ChatChannelState): Unit = {
    this.channelActorState.copy(state = Some(state))
  }

}

// TODO: Where to init the cluster and how to pass store




