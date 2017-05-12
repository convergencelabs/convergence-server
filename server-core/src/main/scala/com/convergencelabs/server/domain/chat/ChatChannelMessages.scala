package com.convergencelabs.server.domain.chat

import java.time.Instant

import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import com.convergencelabs.server.datastore.domain.ChatChannelInfo
import com.convergencelabs.server.domain.model.SessionKey

import akka.actor.ActorRef

object ChatChannelMessages {

  case class CreateChannelRequest(channelId: Option[String], sk: SessionKey, channelType: String,
    channelMembership: String, name: Option[String], topic: Option[String], members: Set[String])

  case class CreateChannelResponse(channelId: String)

  sealed trait ExistingChannelMessage {
    val channelId: String
  }

  // Incoming Messages

  case class RemoveChannelRequest(channelId: String, sk: SessionKey) extends ExistingChannelMessage

  case class JoinChannelRequest(channelId: String, sk: SessionKey, client: ActorRef) extends ExistingChannelMessage
  case class JoinChannelResponse(info: ChatChannelInfo)
  
  case class LeaveChannelRequest(channelId: String, sk: SessionKey, client: ActorRef) extends ExistingChannelMessage
  case class AddUserToChannelRequest(channelId: String, sk: SessionKey, username: String) extends ExistingChannelMessage
  case class RemoveUserFromChannelRequest(channelId: String, sk: SessionKey, username: String) extends ExistingChannelMessage

  case class SetChannelNameRequest(channelId: String, sk: SessionKey, name: String) extends ExistingChannelMessage
  case class SetChannelTopicRequest(channelId: String, sk: SessionKey, topic: String) extends ExistingChannelMessage
  case class MarkChannelEventsSeenRequest(channelId: String, sk: SessionKey, eventNumber: Long) extends ExistingChannelMessage

  case class PublishChatMessageRequest(channelId: String, sk: SessionKey, message: String) extends ExistingChannelMessage
  
  case class UserPermissions(username: String, p: Set[String])
  case class GroupPermissions(groupId: String, p: Set[String])
  
  case class AddChatPermissionsRequest(channelId: String, sk: SessionKey, world: Set[String], user: Set[UserPermissions], group: Set[GroupPermissions]) extends ExistingChannelMessage
  case class RemoveChatPermissionsRequest(channelId: String, sk: SessionKey, world: Set[String], user: Set[UserPermissions], group: Set[GroupPermissions]) extends ExistingChannelMessage
  case class SetChatPermissionsRequest(channelId: String, sk: SessionKey, world: Set[String], user: Set[UserPermissions], group: Set[GroupPermissions]) extends ExistingChannelMessage

  case class GetChannelHistoryRequest(channelId: String, sk: SessionKey, limit: Option[Int], offset: Option[Int],
    forward: Option[Boolean], eventFilter: Option[List[String]]) extends ExistingChannelMessage
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