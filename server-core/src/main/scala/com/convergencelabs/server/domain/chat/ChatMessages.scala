package com.convergencelabs.server.domain.chat

import java.time.Instant

import com.convergencelabs.server.datastore.domain.ChatEvent
import com.convergencelabs.server.datastore.domain.ChatInfo
import com.convergencelabs.server.datastore.domain.ChatMembership
import com.convergencelabs.server.datastore.domain.ChatType
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.DomainUserSessionId

import akka.actor.ActorRef

object ChatMessages {

  trait ChatMessage {

  }

  case class CreateChatRequest(
    chatId: Option[String],
    createdBy: DomainUserSessionId,
    chatType: ChatType.Value,
    membership: ChatMembership.Value,
    name: Option[String],
    topic: Option[String],
    members: Set[DomainUserId]) extends ChatMessage

  case class CreateChannelResponse(channelId: String)

  sealed trait ExistingChatMessage extends ChatMessage {
    val domainFqn: DomainFqn
    val chatId: String
  }

  // Incoming Messages

  case class RemoveChannelRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId) extends ExistingChatMessage

  case class JoinChannelRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, client: ActorRef) extends ExistingChatMessage
  case class JoinChannelResponse(info: ChatInfo)

  case class LeaveChannelRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, client: ActorRef) extends ExistingChatMessage
  case class AddUserToChannelRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, userToAdd: DomainUserId) extends ExistingChatMessage
  case class RemoveUserFromChannelRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, userToRemove: DomainUserId) extends ExistingChatMessage

  case class SetChannelNameRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, name: String) extends ExistingChatMessage
  case class SetChannelTopicRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, topic: String) extends ExistingChatMessage
  case class MarkChannelEventsSeenRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, eventNumber: Long) extends ExistingChatMessage

  case class PublishChatMessageRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, message: String) extends ExistingChatMessage

  case class UserPermissions(user: DomainUserId, permissions: Set[String])
  case class GroupPermissions(groupId: String, permissions: Set[String])

  case class AddChatPermissionsRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChatMessage
  case class RemoveChatPermissionsRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChatMessage
  case class SetChatPermissionsRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChatMessage

  case class GetClientChatPermissionsRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId) extends ExistingChatMessage
  case class GetClientChatPermissionsResponse(permissions: Set[String])

  case class GetWorldChatPermissionsRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId) extends ExistingChatMessage
  case class GetWorldChatPermissionsResponse(permissions: Set[String])

  case class GetAllUserChatPermissionsRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId) extends ExistingChatMessage
  case class GetAllUserChatPermissionsResponse(users: Map[DomainUserId, Set[String]])

  case class GetAllGroupChatPermissionsRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId) extends ExistingChatMessage
  case class GetAllGroupChatPermissionsResponse(groups: Map[String, Set[String]])

  case class GetUserChatPermissionsRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, userId: DomainUserId) extends ExistingChatMessage
  case class GetUserChatPermissionsResponse(permissions: Set[String])

  case class GetGroupChatPermissionsRequest(domainFqn: DomainFqn, chatId: String, requestor: DomainUserSessionId, groupId: String) extends ExistingChatMessage
  case class GetGroupChatPermissionsResponse(permissions: Set[String])

  case class GetChannelHistoryRequest(
    domainFqn: DomainFqn,
    chatId: String,
    requestor: DomainUserSessionId,
    limit: Option[Int],
    startEvent: Option[Long],
    forward: Option[Boolean],
    eventFilter: Option[List[String]]) extends ExistingChatMessage

  case class GetChannelHistoryResponse(events: List[ChatEvent])

  // Outgoing Broadcast Messages
  sealed trait ChatBroadcastMessage {
    val chatId: String
  }
  
  case class UserJoinedChat(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId) extends ChatBroadcastMessage
  case class UserLeftChat(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId) extends ChatBroadcastMessage
  case class UserAddedToChannel(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, addedUserId: DomainUserId) extends ChatBroadcastMessage
  case class UserRemovedFromChannel(chatId: String, eventNumber: Int, timestamp: Instant, userId: DomainUserId, removedUserId: DomainUserId) extends ChatBroadcastMessage
  case class ChatNameChanged(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, name: String) extends ChatBroadcastMessage
  case class ChatTopicChanged(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, topic: String) extends ChatBroadcastMessage
  case class ChannelRemoved(chatId: String) extends ChatBroadcastMessage
  case class RemoteChatMessage(chatId: String, eventNumber: Long, timestamp: Instant, session: DomainUserSessionId, message: String) extends ChatBroadcastMessage

  // Exceptions
  sealed abstract class ChatException(message: String) extends Exception(message)
  case class ChatNotJoinedException(chatId: String) extends ChatException("")
  case class ChatAlreadyJoinedException(chatId: String) extends ChatException("")
  case class ChatNotFoundException(chatId: String) extends ChatException("")
  case class ChatAlreadyExistsException(chatId: String) extends ChatException("")
  case class InvalidChatMessageExcpetion(message: String) extends ChatException(message)
}