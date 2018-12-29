package com.convergencelabs.server.frontend.realtime

import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.SessionKey
import java.time.Instant
import com.google.protobuf.timestamp.Timestamp
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.DateValue
import com.convergencelabs.server.datastore.domain.ChatChannelInfo
import com.convergencelabs.server.datastore.domain.ChatCreatedEvent
import io.convergence.proto.chat.ChatCreatedEventData
import com.convergencelabs.server.datastore.domain.ChatMessageEvent
import com.convergencelabs.server.datastore.domain.ChatUserJoinedEvent
import io.convergence.proto.chat.ChatUserJoinedEventData
import com.convergencelabs.server.datastore.domain.ChatUserLeftEvent
import io.convergence.proto.chat.ChatUserLeftEventData
import com.convergencelabs.server.datastore.domain.ChatUserAddedEvent
import io.convergence.proto.chat.ChatUserAddedEventData
import com.convergencelabs.server.datastore.domain.ChatUserRemovedEvent
import io.convergence.proto.chat.ChatUserRemovedEventData
import com.convergencelabs.server.datastore.domain.ChatNameChangedEvent
import io.convergence.proto.chat.ChatNameChangedEventData
import com.convergencelabs.server.datastore.domain.ChatTopicChangedEvent
import io.convergence.proto.chat.ChatTopicChangedEventData
import io.convergence.proto.chat.ChatMessageEventData
import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import io.convergence.proto.chat.ChatChannelEventData
import com.convergencelabs.server.datastore.domain.ModelPermissions
import io.convergence.proto.model.ModelPermissionsData
import com.convergencelabs.server.domain.PresenceServiceActor.UserPresence
import com.convergencelabs.server.domain.model.ReferenceType
import io.convergence.proto.references.{ReferenceType => ProtoReferenceType}

object ImplicitMessageConversions {
  implicit def sessionKeyToMessage(sessionKey: SessionKey) = io.convergence.proto.authentication.SessionKey(sessionKey.uid, sessionKey.sid)
  implicit def instanceToTimestamp(instant: Instant) = Timestamp(instant.getEpochSecond, instant.getNano)
  implicit def timestampToInstant(timestamp: Timestamp) = Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos)

  implicit def dataValueToMessage(dataValue: DataValue): io.convergence.proto.operations.DataValue =
    dataValue match {
      case value: ObjectValue => io.convergence.proto.operations.DataValue().withObjectValue(objectValueToMessage(value))
      case value: ArrayValue => io.convergence.proto.operations.DataValue().withArrayValue(arrayValueToMessage(value))
      case value: BooleanValue => io.convergence.proto.operations.DataValue().withBooleanValue(booleanValueToMessage(value))
      case value: DoubleValue => io.convergence.proto.operations.DataValue().withDoubleValue(doubleValueToMessage(value))
      case value: NullValue => io.convergence.proto.operations.DataValue().withNullValue(nullValueToMessage(value))
      case value: StringValue => io.convergence.proto.operations.DataValue().withStringValue(stringValueToMessage(value))
      case value: DateValue => io.convergence.proto.operations.DataValue().withDateValue(dateValueToMessage(value))
    }

  implicit def objectValueToMessage(objectValue: ObjectValue) =
    io.convergence.proto.operations.ObjectValue(
      objectValue.id,
      objectValue.children map {
        case (key, value) => (key, dataValueToMessage(value))
      })

  implicit def arrayValueToMessage(arrayValue: ArrayValue) = io.convergence.proto.operations.ArrayValue(arrayValue.id, arrayValue.children.map(dataValueToMessage))
  implicit def booleanValueToMessage(booleanValue: BooleanValue) = io.convergence.proto.operations.BooleanValue(booleanValue.id, booleanValue.value)
  implicit def doubleValueToMessage(doubleValue: DoubleValue) = io.convergence.proto.operations.DoubleValue(doubleValue.id, doubleValue.value)
  implicit def nullValueToMessage(nullValue: NullValue) = io.convergence.proto.operations.NullValue(nullValue.id)
  implicit def stringValueToMessage(stringValue: StringValue) = io.convergence.proto.operations.StringValue(stringValue.id, stringValue.value)
  implicit def dateValueToMessage(dateValue: DateValue) = io.convergence.proto.operations.DateValue(dateValue.id, Some(instanceToTimestamp(dateValue.value)))

  implicit def messageToDataValue(dataValue: io.convergence.proto.operations.DataValue): DataValue =
    dataValue.value match {
      case io.convergence.proto.operations.DataValue.Value.ObjectValue(value) => messageToObjectValue(value)
      case io.convergence.proto.operations.DataValue.Value.ArrayValue(value) => messageToArrayValue(value)
      case io.convergence.proto.operations.DataValue.Value.BooleanValue(value) => messageToBooleanValue(value)
      case io.convergence.proto.operations.DataValue.Value.DoubleValue(value) => messageToDoubleValue(value)
      case io.convergence.proto.operations.DataValue.Value.NullValue(value) => messageToNullValue(value)
      case io.convergence.proto.operations.DataValue.Value.StringValue(value) => messageToStringValue(value)
      case io.convergence.proto.operations.DataValue.Value.DateValue(value) => messageToDateValue(value)
    }

  implicit def messageToObjectValue(objectValue: io.convergence.proto.operations.ObjectValue) =
    ObjectValue(
      objectValue.id,
      objectValue.children map {
        case (key, value) => (key, messageToDataValue(value))
      })

  implicit def messageToArrayValue(arrayValue: io.convergence.proto.operations.ArrayValue) = ArrayValue(arrayValue.id, arrayValue.children.map(messageToDataValue).toList)
  implicit def messageToBooleanValue(booleanValue: io.convergence.proto.operations.BooleanValue) = BooleanValue(booleanValue.id, booleanValue.value)
  implicit def messageToDoubleValue(doubleValue: io.convergence.proto.operations.DoubleValue) = DoubleValue(doubleValue.id, doubleValue.value)
  implicit def messageToNullValue(nullValue: io.convergence.proto.operations.NullValue) = NullValue(nullValue.id)
  implicit def messageToStringValue(stringValue: io.convergence.proto.operations.StringValue) = StringValue(stringValue.id, stringValue.value)
  implicit def messageToDateValue(dateValue: io.convergence.proto.operations.DateValue) = DateValue(dateValue.id, timestampToInstant(dateValue.value.get))

  implicit def channelInfoToMessage(info: ChatChannelInfo) =
    io.convergence.proto.chat.ChatChannelInfoData(info.id, info.channelType, info.isPrivate match {
      case true => "private"
      case false => "public"
    }, info.name, info.topic, Some(info.created), Some(info.lastEventTime), info.lastEventNo, info.members.toSeq)

  implicit def channelEventToMessage(event: ChatChannelEvent): ChatChannelEventData = event match {
    case ChatCreatedEvent(eventNo, channel, user, timestamp, name, topic, members) =>
      ChatChannelEventData().withCreated(
        ChatCreatedEventData(channel, eventNo, Some(timestamp), user, name, topic, members.toSeq));
    case ChatMessageEvent(eventNo, channel, user, timestamp, message) =>
      ChatChannelEventData().withMessage(
        ChatMessageEventData(channel, eventNo, Some(timestamp), user, message))
    case ChatUserJoinedEvent(eventNo, channel, user, timestamp) =>
      ChatChannelEventData().withUserJoined(
        ChatUserJoinedEventData(channel, eventNo, Some(timestamp), user))
    case ChatUserLeftEvent(eventNo, channel, user, timestamp) =>
      ChatChannelEventData().withUserLeft(
        ChatUserLeftEventData(channel, eventNo, Some(timestamp), user))
    case ChatUserAddedEvent(eventNo, channel, user, timestamp, addedUser) =>
      ChatChannelEventData().withUserAdded(
        ChatUserAddedEventData(channel, eventNo, Some(timestamp), addedUser, user))
    case ChatUserRemovedEvent(eventNo, channel, user, timestamp, removedUser) =>
      ChatChannelEventData().withUserRemoved(
        ChatUserRemovedEventData(channel, eventNo, Some(timestamp), removedUser, user))
    case ChatNameChangedEvent(eventNo, channel, user, timestamp, name) =>
      ChatChannelEventData().withNameChanged(
        ChatNameChangedEventData(channel, eventNo, Some(timestamp), user, name))
    case ChatTopicChangedEvent(eventNo, channel, user, timestamp, topic) =>
      ChatChannelEventData().withTopicChanged(
        ChatTopicChangedEventData(channel, eventNo, Some(timestamp), user, topic))
  }

  implicit def modelPermissionsToMessage(permissions: ModelPermissions) =
    ModelPermissionsData(permissions.read, permissions.write, permissions.remove, permissions.manage)

  implicit def userPresenceToMessage(userPresence: UserPresence) = io.convergence.proto.presence.UserPresence(userPresence.username, userPresence.available, userPresence.state)
}
