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
import com.convergencelabs.convergence.server.api.realtime.protocol.CommonProtoConverters.instanceToTimestamp
import com.convergencelabs.convergence.server.api.realtime.protocol.IdentityProtoConverters._
import com.convergencelabs.convergence.server.model.domain.chat._

/**
 * A collection of helper methods to translate domain objects to and from
 * the protocol buffer message classes.
 */
object ChatProtoConverters {
  /**
   * Converts a [[ChatState]] object to the corresponding Protocol Buffer object.
   *
   * @param state The [[ChatState]] domain object.
   * @return The corresponding Protocol Buffer representation.
   */
  def chatStateToProto(state: ChatState): ChatInfoData =
    ChatInfoData(
      state.id,
      chatTypeToProto(state.chatType),
      chatMembershipToProto(state.membership),
      state.name,
      state.topic,
      Some(instanceToTimestamp(state.created)),
      Some(instanceToTimestamp(state.lastEventTime)),
      state.lastEventNumber,
      state.members.values.map(member => ChatMemberData(Some(domainUserIdToProto(member.userId)), member.maxSeenEvent)).toSeq)

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
}
