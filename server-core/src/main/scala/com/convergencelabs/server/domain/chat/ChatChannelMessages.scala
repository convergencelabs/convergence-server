package com.convergencelabs.server.domain.chat

import java.time.Instant

import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import com.convergencelabs.server.datastore.domain.ChatChannelInfo
import com.convergencelabs.server.domain.model.SessionKey

import akka.actor.ActorRef
import com.convergencelabs.server.domain.DomainFqn

object ChatChannelMessages {

  trait ChatChannelMessage {
    
  }

  case class CreateChannelRequest(
    channelId: Option[String],
    sk: SessionKey,
    channelType: String,
    channelMembership: String,
    name: Option[String],
    topic: Option[String],
    members: Set[String]) extends ChatChannelMessage

  case class CreateChannelResponse(channelId: String)

  sealed trait ExistingChannelMessage extends ChatChannelMessage {
    val domainFqn: DomainFqn
    val channelId: String
  }

  // Incoming Messages

  case class RemoveChannelRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey) extends ExistingChannelMessage

  case class JoinChannelRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, client: ActorRef) extends ExistingChannelMessage
  case class JoinChannelResponse(info: ChatChannelInfo)

  case class LeaveChannelRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, client: ActorRef) extends ExistingChannelMessage
  case class AddUserToChannelRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, username: String) extends ExistingChannelMessage
  case class RemoveUserFromChannelRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, username: String) extends ExistingChannelMessage

  case class SetChannelNameRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, name: String) extends ExistingChannelMessage
  case class SetChannelTopicRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, topic: String) extends ExistingChannelMessage
  case class MarkChannelEventsSeenRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, eventNumber: Long) extends ExistingChannelMessage

  case class PublishChatMessageRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, message: String) extends ExistingChannelMessage

  case class UserPermissions(username: String, p: Set[String])
  case class GroupPermissions(groupId: String, p: Set[String])

  case class AddChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChannelMessage
  case class RemoveChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChannelMessage
  case class SetChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChannelMessage

  case class GetClientChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey) extends ExistingChannelMessage
  case class GetClientChatPermissionsResponse(permissions: Set[String])

  case class GetWorldChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey) extends ExistingChannelMessage
  case class GetWorldChatPermissionsResponse(permissions: Set[String])

  case class GetAllUserChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey) extends ExistingChannelMessage
  case class GetAllUserChatPermissionsResponse(users: Map[String, Set[String]])

  case class GetAllGroupChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey) extends ExistingChannelMessage
  case class GetAllGroupChatPermissionsResponse(groups: Map[String, Set[String]])

  case class GetUserChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, username: String, sk: SessionKey) extends ExistingChannelMessage
  case class GetUserChatPermissionsResponse(permissions: Set[String])

  case class GetGroupChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, groupId: String, sk: SessionKey) extends ExistingChannelMessage
  case class GetGroupChatPermissionsResponse(permissions: Set[String])

  case class GetChannelHistoryRequest(domainFqn: DomainFqn, channelId: String, sk: SessionKey, limit: Option[Int], offset: Option[Int],
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