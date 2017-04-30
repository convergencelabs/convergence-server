package com.convergencelabs.server.domain.chat

import java.time.Instant

import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import com.convergencelabs.server.domain.model.SessionKey
import akka.actor.ActorRef

object ChatChannelMessages {

  case class CreateChannelRequest(channelId: Option[String], channelType: String,
    channelMembership: String, name: Option[String], topic: Option[String],
    members: Set[String], createdBy: String)

  case class CreateChannelResponse(channelId: String)

  sealed trait ExistingChannelMessage {
    val channelId: String
  }

  // Incoming Messages

  case class RemoveChannelRequest(channelId: String, username: String) extends ExistingChannelMessage

  case class JoinChannelRequest(channelId: String, sk: SessionKey, client: ActorRef) extends ExistingChannelMessage
  case class LeaveChannelRequest(channelId: String, sk: SessionKey,  client: ActorRef) extends ExistingChannelMessage
  case class AddUserToChannelRequest(channelId: String, username: String, addedBy: String) extends ExistingChannelMessage
  case class RemoveUserFromChannelRequest(channelId: String, username: String, removedBy: String) extends ExistingChannelMessage

  case class SetChannelNameRequest(channelId: String, name: String, setBy: String) extends ExistingChannelMessage
  case class SetChannelTopicRequest(channelId: String, topic: String, setBy: String) extends ExistingChannelMessage
  case class MarkChannelEventsSeenRequest(channelId: String, eventNumber: Long, username: String) extends ExistingChannelMessage

  case class PublishChatMessageRequest(channelId: String, message: String, sk: SessionKey) extends ExistingChannelMessage

  case class GetChannelHistoryRequest(channelId: String, username: String, limit: Option[Long], offset: Option[Long],
    forward: Option[Boolean], events: List[String]) extends ExistingChannelMessage
  case class GetChannelHistoryResponse(events: List[ChatChannelEvent])
  
  // Outgoing Broadcast Messages 
  sealed trait ChatChannelBroadcastMessage
  case class UserJoinedChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String) extends ChatChannelBroadcastMessage
  case class UserLeftChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String) extends ChatChannelBroadcastMessage
  case class UserAddedToChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String, addedBy: String) extends ChatChannelBroadcastMessage
  case class UserRemovedFromChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String, removedBy: String) extends ChatChannelBroadcastMessage
  case class ChannelNameChanged(channelId: String, eventNumber: Long, timestamp: Instant, name: String, setBy: String) extends ChatChannelBroadcastMessage
  case class ChannelTopicChanged(channelId: String, eventNumber: Long, timestamp: Instant, topic: String, setBy: String) extends ChatChannelBroadcastMessage

  case class ChannelRemoved(channelId: String) extends ChatChannelBroadcastMessage

  case class RemoteChatMessage(channelId: String, eventNumber: Long, timestamp: Instant, sk: SessionKey, message: String) extends ChatChannelBroadcastMessage

  // Exceptions
  sealed abstract class ChatChannelException(message: String) extends Exception(message)
  case class ChannelNotJoinedException(channelId: String) extends ChatChannelException("")
  case class ChannelAlreadyJoinedException(channelId: String) extends ChatChannelException("")
  case class ChannelNotFoundException(channelId: String) extends ChatChannelException("")
  case class ChannelAlreadyExistsException(channelId: String) extends ChatChannelException("")
  case class InvalidChannelMessageExcpetion(message: String) extends ChatChannelException(message)  
}