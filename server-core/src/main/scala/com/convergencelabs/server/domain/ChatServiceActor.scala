package com.convergencelabs.server.domain

import java.time.Instant

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.model.SessionKey

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish

object ChatServiceActor {

  val RelativePath = "chatRelayService"

  def props(domainFqn: DomainFqn): Props = Props(
    new ChatServiceActor(domainFqn))

  // Incoming Messages
  case class CreateChannelRequest(channelId: Option[String], channelType: String, 
      channelMembership: String, name: Option[String], topic: Option[String],
      members: List[String])
  case class RemoveChannelRequest(channelId: String, username: String)
  
  case class JoinChannelRequest(channelId: String, username: String)
  case class LeaveChannelRequest(channelId: String, username: String)
  case class AddUserToChannelRequest(channelId: String, username: String, addedBy: String)
  case class RemoveUserFromChannelRequest(channelId: String, username: String, removedBy: String)
  
  case class SetChannelNameRequest(channelId: String, name: String, setBy: String)
  case class SetChannelTopicRequest(channelId: String, topic: String, setBy: String)
  case class MarkChannelEventsSeenRequest(channelId: String, eventNumber: Long, username: String)
  
  case class GetChannelsRequest(username: String)
  case class GetJoinedChannelsRequest(username: String)
  case class GetDirectChannelsRequest(username: String, userLists: List[List[String]])
  case class ChannelHistoryRequest(username: String, channleId: String, limit: Option[Int], offset: Option[Int], forward: Option[Boolean], events: List[String])
  
  case class PublishChatMessageRequest(sk: SessionKey, channelId: String, message: String)

  // Outgoing Response Messages
  case class CreateChannelResponse(channelId: String)
  
  // Outgoing Broadcast Messages 
  case class UserJoinedChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String)
  case class UserLeftChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String)
  case class UserAddedToChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String, addedBy: String)
  case class UserRemovedFromChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String, removedBy: String)

  case class ChannelJoined(channelId: String, username: String)
  case class ChannelLeft(channelId: String, username: String)
  case class ChannelRemoved(channelId: String)

  case class RemoteChatMessage(channelId: String, eventNumber: Long, timestamp: Instant, sk: SessionKey, message: String)
  
  

  def getChatUsernameTopicName(username: String): String = {
    return s"chat-user-${username}"
  }
}

class ChatServiceActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  import ChatServiceActor._

  val mediator = DistributedPubSub(context.system).mediator
  
  def receive: Receive = {
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
      case message: GetChannelsRequest =>
        onGetChannels(message)
      case message: GetJoinedChannelsRequest =>
        onGetJoinedChannels(message)
      case message: GetDirectChannelsRequest =>
        onGetDirect(message)
      case message: ChannelHistoryRequest =>
        onGetHistory(message)
      case message: PublishChatMessageRequest =>
        onPublishMessage(message)
  }
  
  def onCreateChannel(message: CreateChannelRequest): Unit = {
    val CreateChannelRequest(channelId, channelType, channelMembership, name, topic, members) = message;
    ???
  }

  def onRemoveChannel(message: RemoveChannelRequest): Unit = {
    val RemoveChannelRequest(channelId, username) = message;
    ???
  }

  def onJoinChannel(message: JoinChannelRequest): Unit = {
    val JoinChannelRequest(channelId, username) = message;
    ???
  }

  def onLeaveChannel(message: LeaveChannelRequest): Unit = {
    val LeaveChannelRequest(channelId, username) = message;
    ???
  }

  def onAddUserToChannel(message: AddUserToChannelRequest): Unit = {
    val AddUserToChannelRequest(channelId, username, addedBy) = message;
    ???
  }

  def onRemoveUserFromChannel(message: RemoveUserFromChannelRequest): Unit = {
    val RemoveUserFromChannelRequest(channelId, username, removedBy) = message;
    ???
  }

  def onSetChatChannelName(message: SetChannelNameRequest): Unit = {
    val SetChannelNameRequest(channelId, name, setBy) = message;
    ???
  }

  def onSetChatChannelTopic(message: SetChannelTopicRequest): Unit = {
    val SetChannelTopicRequest(channelId, topic, setBy) = message;
    ???
  }

  def onMarkEventsSeen(message: MarkChannelEventsSeenRequest): Unit = {
    val MarkChannelEventsSeenRequest(channelId, eventNumber, username) = message;
    ???
  }

  def onGetChannels(message: GetChannelsRequest): Unit = {
    val GetChannelsRequest(username) = message
    ???
  }

  def onGetDirect(message: GetDirectChannelsRequest): Unit = {
    val GetDirectChannelsRequest(username, usernameLists) = message;
    ???
  }

  def onGetJoinedChannels(message: GetJoinedChannelsRequest): Unit = {
    val GetJoinedChannelsRequest(username) = message
    ???
  }

  def onGetHistory(message: ChannelHistoryRequest): Unit = {
    val ChannelHistoryRequest(username, channleId, limit, offset, forward, events) = message;
    ???
  }

  def onPublishMessage(message: PublishChatMessageRequest): Unit = {
    val PublishChatMessageRequest(sk, channeId, msg) = message;
    ???
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
}

