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

package com.convergencelabs.convergence.server.api.realtime.protocol

import com.convergencelabs.convergence.proto.chat._
import com.convergencelabs.convergence.proto.core.{PermissionsList, UserPermissionsEntry}
import com.convergencelabs.convergence.server.api.realtime.protocol.CommonProtoConverters.instanceToTimestamp
import com.convergencelabs.convergence.server.api.realtime.protocol.IdentityProtoConverters._
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{GroupPermissions, UserPermissions}
import com.convergencelabs.convergence.server.model.domain.chat._

/**
 * A collection of helper methods to translate domain objects to and from
 * the protocol buffer message classes.
 */
object ChatProtoConverters {
  /**
   * Converts a ChatInfo object to the corresponding Protocol Buffer object.
   *
   * @param info The ChatInfo domain object.
   * @return The corresponding Protocol Buffer representation.
   */
  def chatInfoToProto(info: ChatInfo): ChatInfoData =
    ChatInfoData(
      info.id,
      chatTypeToProto(info.chatType),
      chatMembershipToProto(info.membership),
      info.name,
      info.topic,
      Some(instanceToTimestamp(info.created)),
      Some(instanceToTimestamp(info.lastEventTime)),
      info.lastEventNumber,
      info.members.map(member => ChatMemberData(Some(domainUserIdToProto(member.userId)), member.seen)).toSeq)

  /**
   * Converts a [[ChatType]] to a String for inclusion in the Protocol
   * Buffers message.
   *
   * @param chatType The [[ChatType]] to convert.
   * @return The proper string representation of the [[ChatType]].
   */
  def chatTypeToProto(chatType: ChatType.Value): String = {
    chatType match {
      case ChatType.Channel => "channel"
      case ChatType.Room => "room"
      case ChatType.Direct => "direct"
    }
  }

  /**
   * Converts a [[ChatMembership]] to a String for inclusion in the Protocol
   * Buffers message.
   *
   * @param membership The [[ChatMembership]] to convert.
   * @return The proper string representation of the [[ChatMembership]].
   */
  def chatMembershipToProto(membership: ChatMembership.Value): String = {
    membership match {
      case ChatMembership.Public => "public"
      case ChatMembership.Private => "private"
    }
  }

  /**
   * Converts a ChatEvent into a Protocol Buffer representation.
   *
   * @param event The chat event to convert.
   * @return The Protocol Buffer representation of the Chat Event.
   */
  def chatEventToProto(event: ChatEvent): ChatEventData = event match {
    case ChatCreatedEvent(eventNumber, chatId, user, timestamp, name, topic, members) =>
      ChatEventData().withCreated(
        ChatCreatedEventData(chatId, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), name, topic, members.map(domainUserIdToProto).toSeq));
    case ChatMessageEvent(eventNumber, chatId, user, timestamp, message) =>
      ChatEventData().withMessage(
        ChatMessageEventData(chatId, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), message))
    case ChatUserJoinedEvent(eventNumber, chatId, user, timestamp) =>
      ChatEventData().withUserJoined(
        ChatUserJoinedEventData(chatId, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user))))
    case ChatUserLeftEvent(eventNumber, chatId, user, timestamp) =>
      ChatEventData().withUserLeft(
        ChatUserLeftEventData(chatId, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user))))
    case ChatUserAddedEvent(eventNumber, chatId, user, timestamp, addedUser) =>
      ChatEventData().withUserAdded(
        ChatUserAddedEventData(chatId, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), Some(domainUserIdToProto(addedUser))))
    case ChatUserRemovedEvent(eventNumber, chatId, user, timestamp, removedUser) =>
      ChatEventData().withUserRemoved(
        ChatUserRemovedEventData(chatId, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), Some(domainUserIdToProto(removedUser))))
    case ChatNameChangedEvent(eventNumber, chatId, user, timestamp, name) =>
      ChatEventData().withNameChanged(
        ChatNameChangedEventData(chatId, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), name))
    case ChatTopicChangedEvent(eventNumber, chatId, user, timestamp, topic) =>
      ChatEventData().withTopicChanged(
        ChatTopicChangedEventData(chatId, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), topic))
  }

  /**
   * Converts a map of group permissions in a Protocol Buffer representation
   * into a Set of domain GroupPermissions.
   *
   * @param groupPermissionData The Protocol Buffer group permissions map.
   * @return A Set of domain GroupPermission.
   */
  def protoToGroupPermissions(groupPermissionData: Map[String, PermissionsList]): Set[GroupPermissions] = {
    groupPermissionData.map {
      case (groupId, permissions) => (groupId, GroupPermissions(groupId, permissions.values.toSet))
    }.values.toSet
  }

  /**
   * Converts a Seq of user permissions in a Protocol Buffer representation
   * into a Set of domain UserPermissions.
   *
   * @param userPermissionData The Protocol Buffer group permissions map.
   * @return A Set of domain UserPermissions.
   */
  def protoToUserPermissions(userPermissionData: Seq[UserPermissionsEntry]): Set[UserPermissions] = {
    userPermissionData
      .map(p => permissions.UserPermissions(protoToDomainUserId(p.user.get), p.permissions.toSet)).toSet
  }
}
