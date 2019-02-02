package com.convergencelabs.server.domain.chat

import java.time.Instant

import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import com.convergencelabs.server.datastore.domain.ChatChannelInfo

import akka.actor.ActorRef
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUserSessionId
import com.convergencelabs.server.domain.DomainUserId

object ChatChannelMessages {

  trait ChatChannelMessage {

  }

  case class CreateChannelRequest(
    channelId: Option[String],
    createdBy: DomainUserSessionId,
    channelType: String,
    channelMembership: String,
    name: Option[String],
    topic: Option[String],
    members: Set[DomainUserId]) extends ChatChannelMessage

  case class CreateChannelResponse(channelId: String)

  sealed trait ExistingChannelMessage extends ChatChannelMessage {
    val domainFqn: DomainFqn
    val channelId: String
  }

  // Incoming Messages

  case class RemoveChannelRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId) extends ExistingChannelMessage

  case class JoinChannelRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, client: ActorRef) extends ExistingChannelMessage
  case class JoinChannelResponse(info: ChatChannelInfo)

  case class LeaveChannelRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, client: ActorRef) extends ExistingChannelMessage
  case class AddUserToChannelRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, userToAdd: DomainUserId) extends ExistingChannelMessage
  case class RemoveUserFromChannelRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, userToRemove: DomainUserId) extends ExistingChannelMessage

  case class SetChannelNameRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, name: String) extends ExistingChannelMessage
  case class SetChannelTopicRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, topic: String) extends ExistingChannelMessage
  case class MarkChannelEventsSeenRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, eventNumber: Long) extends ExistingChannelMessage

  case class PublishChatMessageRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, message: String) extends ExistingChannelMessage

  case class UserPermissions(user: DomainUserId, permissions: Set[String])
  case class GroupPermissions(groupId: String, permissions: Set[String])

  case class AddChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChannelMessage
  case class RemoveChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChannelMessage
  case class SetChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChannelMessage

  case class GetClientChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId) extends ExistingChannelMessage
  case class GetClientChatPermissionsResponse(permissions: Set[String])

  case class GetWorldChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId) extends ExistingChannelMessage
  case class GetWorldChatPermissionsResponse(permissions: Set[String])

  case class GetAllUserChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId) extends ExistingChannelMessage
  case class GetAllUserChatPermissionsResponse(users: Map[DomainUserId, Set[String]])

  case class GetAllGroupChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId) extends ExistingChannelMessage
  case class GetAllGroupChatPermissionsResponse(groups: Map[String, Set[String]])

  case class GetUserChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, userId: DomainUserId) extends ExistingChannelMessage
  case class GetUserChatPermissionsResponse(permissions: Set[String])

  case class GetGroupChatPermissionsRequest(domainFqn: DomainFqn, channelId: String, requestor: DomainUserSessionId, groupId: String) extends ExistingChannelMessage
  case class GetGroupChatPermissionsResponse(permissions: Set[String])

  case class GetChannelHistoryRequest(
    domainFqn: DomainFqn,
    channelId: String,
    requestor: DomainUserSessionId,
    limit: Option[Int],
    startEvent: Option[Long],
    forward: Option[Boolean],
    eventFilter: Option[List[String]]) extends ExistingChannelMessage

  case class GetChannelHistoryResponse(events: List[ChatChannelEvent])

  // Outgoing Broadcast Messages
  sealed trait ChatChannelBroadcastMessage
  case class UserJoinedChannel(channelId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId) extends ChatChannelBroadcastMessage
  case class UserLeftChannel(channelId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId) extends ChatChannelBroadcastMessage
  case class UserAddedToChannel(channelId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, addedUserId: DomainUserId) extends ChatChannelBroadcastMessage
  case class UserRemovedFromChannel(channelId: String, eventNumber: Int, timestamp: Instant, userId: DomainUserId, removedUserId: DomainUserId) extends ChatChannelBroadcastMessage
  case class ChannelNameChanged(channelId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, name: String) extends ChatChannelBroadcastMessage
  case class ChannelTopicChanged(channelId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, topic: String) extends ChatChannelBroadcastMessage

  case class ChannelRemoved(channelId: String) extends ChatChannelBroadcastMessage

  case class RemoteChatMessage(channelId: String, eventNumber: Long, timestamp: Instant, session: DomainUserSessionId, message: String) extends ChatChannelBroadcastMessage

  // Exceptions
  sealed abstract class ChatChannelException(message: String) extends Exception(message)
  case class ChannelNotJoinedException(channelId: String) extends ChatChannelException("")
  case class ChannelAlreadyJoinedException(channelId: String) extends ChatChannelException("")
  case class ChannelNotFoundException(channelId: String) extends ChatChannelException("")
  case class ChannelAlreadyExistsException(channelId: String) extends ChatChannelException("")
  case class InvalidChannelMessageExcpetion(message: String) extends ChatChannelException(message)
}