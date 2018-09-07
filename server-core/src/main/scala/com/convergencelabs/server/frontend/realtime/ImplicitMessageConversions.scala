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
import convergence.protocol.chat.ChatCreatedEventData
import com.convergencelabs.server.datastore.domain.ChatMessageEvent
import com.convergencelabs.server.datastore.domain.ChatUserJoinedEvent
import convergence.protocol.chat.ChatUserJoinedEventData
import com.convergencelabs.server.datastore.domain.ChatUserLeftEvent
import convergence.protocol.chat.ChatUserLeftEventData
import com.convergencelabs.server.datastore.domain.ChatUserAddedEvent
import convergence.protocol.chat.ChatUserAddedEventData
import com.convergencelabs.server.datastore.domain.ChatUserRemovedEvent
import convergence.protocol.chat.ChatUserRemovedEventData
import com.convergencelabs.server.datastore.domain.ChatNameChangedEvent
import convergence.protocol.chat.ChatNameChangedEventData
import com.convergencelabs.server.datastore.domain.ChatTopicChangedEvent
import convergence.protocol.chat.ChatTopicChangedEventData
import convergence.protocol.chat.ChatMessageEventData
import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import convergence.protocol.chat.ChatChannelEventData
import com.convergencelabs.server.datastore.domain.ModelPermissions
import convergence.protocol.model.ModelPermissionsData
import com.convergencelabs.server.domain.PresenceServiceActor.UserPresence

object ImplicitMessageConversions {
  implicit def sessionKeyToMessage(sessionKey: SessionKey) = convergence.protocol.authentication.SessionKey(sessionKey.uid, sessionKey.sid)
  implicit def instanceToTimestamp(instant: Instant) = Timestamp(instant.getEpochSecond, instant.getNano)
  implicit def timestampToInstant(timestamp: Timestamp) = Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos)

  implicit def dataValueToMessage(dataValue: DataValue): convergence.protocol.operations.DataValue =
    dataValue match {
      case value: ObjectValue  => convergence.protocol.operations.DataValue().withObjectValue(objectValueToMessage(value))
      case value: ArrayValue   => convergence.protocol.operations.DataValue().withArrayValue(arrayValueToMessage(value))
      case value: BooleanValue => convergence.protocol.operations.DataValue().withBooleanValue(booleanValueToMessage(value))
      case value: DoubleValue  => convergence.protocol.operations.DataValue().withDoubleValue(doubleValueToMessage(value))
      case value: NullValue    => convergence.protocol.operations.DataValue().withNullValue(nullValueToMessage(value))
      case value: StringValue  => convergence.protocol.operations.DataValue().withStringValue(stringValueToMessage(value))
      case value: DateValue    => convergence.protocol.operations.DataValue().withDateValue(dateValueToMessage(value))
    }

  implicit def objectValueToMessage(objectValue: ObjectValue) =
    convergence.protocol.operations.ObjectValue(
      objectValue.id,
      objectValue.children map {
        case (key, value) => (key, dataValueToMessage(value))
      })

  implicit def arrayValueToMessage(arrayValue: ArrayValue) = convergence.protocol.operations.ArrayValue(arrayValue.id, arrayValue.children.map(dataValueToMessage))
  implicit def booleanValueToMessage(booleanValue: BooleanValue) = convergence.protocol.operations.BooleanValue(booleanValue.id, booleanValue.value)
  implicit def doubleValueToMessage(doubleValue: DoubleValue) = convergence.protocol.operations.DoubleValue(doubleValue.id, doubleValue.value)
  implicit def nullValueToMessage(nullValue: NullValue) = convergence.protocol.operations.NullValue(nullValue.id)
  implicit def stringValueToMessage(stringValue: StringValue) = convergence.protocol.operations.StringValue(stringValue.id, stringValue.value)
  implicit def dateValueToMessage(dateValue: DateValue) = convergence.protocol.operations.DateValue(dateValue.id, Some(instanceToTimestamp(dateValue.value)))

  
  implicit def messageToDataValue(dataValue: convergence.protocol.operations.DataValue): DataValue =
    dataValue.value match {
      case convergence.protocol.operations.DataValue.Value.ObjectValue(value)  => messageToObjectValue(value)
      case convergence.protocol.operations.DataValue.Value.ArrayValue(value)   => messageToArrayValue(value)
      case convergence.protocol.operations.DataValue.Value.BooleanValue(value) => messageToBooleanValue(value)
      case convergence.protocol.operations.DataValue.Value.DoubleValue(value)  => messageToDoubleValue(value)
      case convergence.protocol.operations.DataValue.Value.NullValue(value)    => messageToNullValue(value)
      case convergence.protocol.operations.DataValue.Value.StringValue(value)  => messageToStringValue(value)
      case convergence.protocol.operations.DataValue.Value.DateValue(value)    => messageToDateValue(value)
    }
  
  implicit def messageToObjectValue(objectValue: convergence.protocol.operations.ObjectValue) =
    ObjectValue(
        objectValue.id,
        objectValue.children map {
          case (key, value) => (key, messageToDataValue(value))
        })
        
  implicit def messageToArrayValue(arrayValue: convergence.protocol.operations.ArrayValue) = ArrayValue(arrayValue.id, arrayValue.children.map(messageToDataValue).toList)
  implicit def messageToBooleanValue(booleanValue: convergence.protocol.operations.BooleanValue) = BooleanValue(booleanValue.id, booleanValue.value)
  implicit def messageToDoubleValue(doubleValue: convergence.protocol.operations.DoubleValue) = DoubleValue(doubleValue.id, doubleValue.value)
  implicit def messageToNullValue(nullValue: convergence.protocol.operations.NullValue) = NullValue(nullValue.id)
  implicit def messageToStringValue(stringValue: convergence.protocol.operations.StringValue) = StringValue(stringValue.id, stringValue.value)
  implicit def messageToDateValue(dateValue: convergence.protocol.operations.DateValue) = DateValue(dateValue.id, timestampToInstant(dateValue.value.get))
  
  
  implicit def channelInfoToMessage(info: ChatChannelInfo) =
    convergence.protocol.chat.ChatChannelInfoData(info.id, info.channelType, info.isPrivate match {
      case true  => "private"
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
    
  implicit def userPresenceToMessage(userPresence: UserPresence) = convergence.protocol.presence.UserPresence(userPresence.username, userPresence.available, userPresence.state)
}


  
