/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.chat

import java.time.Instant

import akka.actor.ActorRef
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.datastore.domain.{ChatEvent, ChatInfo}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId, DomainUserSessionId}

object ChatMessages {

  sealed trait ExistingChatMessage {
    val domainId: DomainId
    val chatId: String
  }

  // Incoming Messages

  case class RemoveChatRequest(domainId: DomainId, chatId: String, requester: DomainUserId) extends ExistingChatMessage

  case class JoinChannelRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId, client: ActorRef) extends ExistingChatMessage

  case class JoinChannelResponse(info: ChatInfo)

  case class LeaveChannelRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId, client: ActorRef) extends ExistingChatMessage

  case class AddUserToChannelRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId, userToAdd: DomainUserId) extends ExistingChatMessage

  case class RemoveUserFromChannelRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId, userToRemove: DomainUserId) extends ExistingChatMessage

  case class SetChatNameRequest(domainId: DomainId, chatId: String, requester: DomainUserId, name: String) extends ExistingChatMessage

  case class SetChatTopicRequest(domainId: DomainId, chatId: String, requester: DomainUserId, topic: String) extends ExistingChatMessage

  case class MarkChannelEventsSeenRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId, eventNumber: Long) extends ExistingChatMessage

  case class PublishChatMessageRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId, message: String) extends ExistingChatMessage

  case class UserPermissions(user: DomainUserId, permissions: Set[String])

  case class GroupPermissions(groupId: String, permissions: Set[String])

  case class AddChatPermissionsRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChatMessage

  case class RemoveChatPermissionsRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChatMessage

  case class SetChatPermissionsRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]) extends ExistingChatMessage

  case class GetClientChatPermissionsRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId) extends ExistingChatMessage

  case class GetClientChatPermissionsResponse(permissions: Set[String])

  case class GetWorldChatPermissionsRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId) extends ExistingChatMessage

  case class GetWorldChatPermissionsResponse(permissions: Set[String])

  case class GetAllUserChatPermissionsRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId) extends ExistingChatMessage

  case class GetAllUserChatPermissionsResponse(users: Map[DomainUserId, Set[String]])

  case class GetAllGroupChatPermissionsRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId) extends ExistingChatMessage

  case class GetAllGroupChatPermissionsResponse(groups: Map[String, Set[String]])

  case class GetUserChatPermissionsRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId, userId: DomainUserId) extends ExistingChatMessage

  case class GetUserChatPermissionsResponse(permissions: Set[String])

  case class GetGroupChatPermissionsRequest(domainId: DomainId, chatId: String, requester: DomainUserSessionId, groupId: String) extends ExistingChatMessage

  case class GetGroupChatPermissionsResponse(permissions: Set[String])

  case class GetChatHistoryRequest(domainId: DomainId,
                                   chatId: String,
                                   requester: Option[DomainUserSessionId],
                                   offset: Option[Int],
                                   limit: Option[Int],
                                   startEvent: Option[Long],
                                   forward: Option[Boolean],
                                   eventTypes: Option[Set[String]],
                                   filter: Option[String] = None) extends ExistingChatMessage

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

  case class ChatNotJoinedException(chatId: String) extends ChatException(s"Can not perform this action on a chat that is not joined")

  case class ChatAlreadyJoinedException(chatId: String) extends ChatException("")

  case class ChatNotFoundException(chatId: String) extends ChatException("")

  case class ChatAlreadyExistsException(chatId: String) extends ChatException("")

  case class InvalidChatMessageException(message: String) extends ChatException(message)

}