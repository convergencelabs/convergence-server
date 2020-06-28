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
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.domain.chat.{GroupPermissions, UserPermissions}

object ChatProtoConverters {
  def chatInfoToMessage(info: ChatInfo): ChatInfoData =
    com.convergencelabs.convergence.proto.chat.ChatInfoData(
      info.id,
      info.chatType.toString.toLowerCase(),
      info.membership.toString.toLowerCase(),
      info.name,
      info.topic,
      Some(instanceToTimestamp(info.created)),
      Some(instanceToTimestamp(info.lastEventTime)),
      info.lastEventNumber,
      info.members.map(member => ChatMemberData(Some(domainUserIdToProto(member.userId)), member.seen)).toSeq)

  def channelEventToMessage(event: ChatEvent): ChatEventData = event match {
    case ChatCreatedEvent(eventNumber, channel, user, timestamp, name, topic, members) =>
      ChatEventData().withCreated(
        ChatCreatedEventData(channel, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), name, topic, members.map(domainUserIdToProto).toSeq));
    case ChatMessageEvent(eventNumber, channel, user, timestamp, message) =>
      ChatEventData().withMessage(
        ChatMessageEventData(channel, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), message))
    case ChatUserJoinedEvent(eventNumber, channel, user, timestamp) =>
      ChatEventData().withUserJoined(
        ChatUserJoinedEventData(channel, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user))))
    case ChatUserLeftEvent(eventNumber, channel, user, timestamp) =>
      ChatEventData().withUserLeft(
        ChatUserLeftEventData(channel, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user))))
    case ChatUserAddedEvent(eventNumber, channel, user, timestamp, addedUser) =>
      ChatEventData().withUserAdded(
        ChatUserAddedEventData(channel, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), Some(domainUserIdToProto(addedUser))))
    case ChatUserRemovedEvent(eventNumber, channel, user, timestamp, removedUser) =>
      ChatEventData().withUserRemoved(
        ChatUserRemovedEventData(channel, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), Some(domainUserIdToProto(removedUser))))
    case ChatNameChangedEvent(eventNumber, channel, user, timestamp, name) =>
      ChatEventData().withNameChanged(
        ChatNameChangedEventData(channel, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), name))
    case ChatTopicChangedEvent(eventNumber, channel, user, timestamp, topic) =>
      ChatEventData().withTopicChanged(
        ChatTopicChangedEventData(channel, eventNumber, Some(instanceToTimestamp(timestamp)), Some(domainUserIdToProto(user)), topic))
  }


  def mapGroupPermissions(groupPermissionData: Map[String, PermissionsList]): Set[GroupPermissions] = {
    groupPermissionData.map {
      case (groupId, permissions) => (groupId, GroupPermissions(groupId, permissions.values.toSet))
    }.values.toSet
  }

  def mapUserPermissions(userPermissionData: Seq[UserPermissionsEntry]): Set[UserPermissions] = {
    userPermissionData
      .map(p => UserPermissions(protoToDomainUserId(p.user.get), p.permissions.toSet)).toSet
  }
}
