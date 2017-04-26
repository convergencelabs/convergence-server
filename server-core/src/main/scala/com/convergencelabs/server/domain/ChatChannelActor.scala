package com.convergencelabs.server.domain

import java.time.Instant

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.model.SessionKey

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import scala.util.Try
import scala.util.Success
import akka.actor.Status

object ChatChannelActor {

  def props(domainFqn: DomainFqn): Props = Props(
    new ChatChannelActor(domainFqn))

  sealed trait ChatChannelMessage {
    val channelId: String
  }

  // Incoming Messages
  case class CreateChannelRequest(channelId: String, channelType: String,
    channelMembership: String, name: Option[String], topic: Option[String],
    members: List[String]) extends ChatChannelMessage
  case class CreateChannelResponse(channelId: String) extends ChatChannelMessage

  case class RemoveChannelRequest(channelId: String, username: String) extends ChatChannelMessage

  case class JoinChannelRequest(channelId: String, username: String) extends ChatChannelMessage
  case class LeaveChannelRequest(channelId: String, username: String) extends ChatChannelMessage
  case class AddUserToChannelRequest(channelId: String, username: String, addedBy: String) extends ChatChannelMessage
  case class RemoveUserFromChannelRequest(channelId: String, username: String, removedBy: String) extends ChatChannelMessage

  case class SetChannelNameRequest(channelId: String, name: String, setBy: String) extends ChatChannelMessage
  case class SetChannelTopicRequest(channelId: String, topic: String, setBy: String) extends ChatChannelMessage
  case class MarkChannelEventsSeenRequest(channelId: String, eventNumber: Long, username: String) extends ChatChannelMessage

  case class PublishChatMessageRequest(channelId: String, sk: SessionKey, message: String) extends ChatChannelMessage

  case class ChannelHistoryRequest(channelId: String, username: String, limit: Option[Int], offset: Option[Int],
    forward: Option[Boolean], events: List[String]) extends ChatChannelMessage
  case class ChannelHistoryResponse(events: List[ChatChannelEvent])

  // Outgoing Broadcast Messages 
  case class UserJoinedChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String) extends ChatChannelMessage
  case class UserLeftChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String) extends ChatChannelMessage
  case class UserAddedToChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String, addedBy: String) extends ChatChannelMessage
  case class UserRemovedFromChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String, removedBy: String) extends ChatChannelMessage

  case class ChannelJoined(channelId: String, username: String) extends ChatChannelMessage
  case class ChannelLeft(channelId: String, username: String) extends ChatChannelMessage
  case class ChannelRemoved(channelId: String) extends ChatChannelMessage

  case class RemoteChatMessage(channelId: String, eventNumber: Long, timestamp: Instant, sk: SessionKey, message: String) extends ChatChannelMessage

  // Exceptions
  case class ChannelNotJoinedException(channelId: String) extends Exception()
  case class ChannelNotFoundException(channelId: String) extends Exception()
  case class ChannelAlreadyExistsException(channelId: String) extends Exception()

  def getChatUsernameTopicName(username: String): String = {
    return s"chat-user-${username}"
  }
}

class ChatChannelActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  import ChatChannelActor._

  val mediator = DistributedPubSub(context.system).mediator

  // FIXME this is not really the right object, I need membership info also.
  // Here None signifies that the channel does not exist.
  var channelState: Option[ChatChannelState] = None

  // Default recieve will be called the first time
  def receive: Receive = {
    case message: ChatChannelMessage =>
      initialize(message.channelId)
        .recover { case cause: Exception => this.unexpectedError(message, cause) }
        .flatMap(_ => handleChatMessage(message))
        .recover { case cause: Exception => this.unexpectedError(message, cause) }

    case unhandled: Any => this.unhandled(unhandled)
  }

  def receiveWhenInitizlized: Receive = {
    case message: ChatChannelMessage =>
      handleChatMessage(message)
      .recover { case cause: Exception => this.unexpectedError(message, cause) }
    case unhandled: Any =>
      this.unhandled(unhandled)
  }

  def handleChatMessage: PartialFunction[ChatChannelMessage, Try[Unit]] = {
    case message: CreateChannelRequest =>
      onCreateChannel(message)
    case message: RemoveChannelRequest =>
      onRemoveChannel(message)
    case message: JoinChannelRequest =>
      onJoinChannel(message)
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

  def onCreateChannel(message: CreateChannelRequest): Try[Unit] = {
    val CreateChannelRequest(channelId, channelType, channelMembership, name, topic, members) = message;
    ???
  }

  def onRemoveChannel(message: RemoveChannelRequest): Try[Unit] = {
    val RemoveChannelRequest(channelId, username) = message;
    ???
  }

  def onJoinChannel(message: JoinChannelRequest): Try[Unit] = {
    val JoinChannelRequest(channelId, username) = message;
    ???
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

  private[this] def getChatChannelMembers(channelId: String): List[String] = {
    ???
  }

  private[this] def initialize(channelId: String): Try[Unit] = {
    // Load crap from the database?
    // Where do I get the chat channel store from?
    this.channelState = Some(
      ChatChannelState(
        channelId,
        "group",
        Instant.now(),
        false,
        "myname",
        "mytopic",
        Instant.now(),
        7,
        Set("michael", "cameron")))
    context.become(receiveWhenInitizlized)
    Success(())
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
    membrers: Set[String])

}



