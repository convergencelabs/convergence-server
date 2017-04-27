package com.convergencelabs.server.domain

import java.time.Instant

import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import com.convergencelabs.server.domain.model.SessionKey

object ChatChannelMessages {
  sealed trait ChatChannelMessage {
    val channelId: String
  }

  sealed trait ExistingChannelMessage extends ChatChannelMessage

  // Incoming Messages
  case class CreateChannelRequest(channelId: String, channelType: String,
    channelMembership: String, name: Option[String], topic: Option[String],
    members: List[String]) extends ChatChannelMessage
  case class CreateChannelResponse(channelId: String)

  case class RemoveChannelRequest(channelId: String, username: String) extends ExistingChannelMessage

  case class JoinChannelRequest(channelId: String, username: String) extends ExistingChannelMessage
  case class LeaveChannelRequest(channelId: String, username: String) extends ExistingChannelMessage
  case class AddUserToChannelRequest(channelId: String, username: String, addedBy: String) extends ExistingChannelMessage
  case class RemoveUserFromChannelRequest(channelId: String, username: String, removedBy: String) extends ExistingChannelMessage

  case class SetChannelNameRequest(channelId: String, name: String, setBy: String) extends ExistingChannelMessage
  case class SetChannelTopicRequest(channelId: String, topic: String, setBy: String) extends ExistingChannelMessage
  case class MarkChannelEventsSeenRequest(channelId: String, eventNumber: Long, username: String) extends ExistingChannelMessage

  case class PublishChatMessageRequest(channelId: String, sk: SessionKey, message: String) extends ExistingChannelMessage

  case class ChannelHistoryRequest(channelId: String, username: String, limit: Option[Int], offset: Option[Int],
    forward: Option[Boolean], events: List[String]) extends ExistingChannelMessage
  case class ChannelHistoryResponse(events: List[ChatChannelEvent])

  // Outgoing Broadcast Messages 
  case class UserJoinedChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String)
  case class UserLeftChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String)
  case class UserAddedToChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String, addedBy: String)
  case class UserRemovedFromChannel(channelId: String, eventNumber: Long, timestamp: Instant, username: String, removedBy: String)

  case class ChannelJoined(channelId: String, username: String)
  case class ChannelLeft(channelId: String, username: String)
  case class ChannelRemoved(channelId: String)

  case class RemoteChatMessage(channelId: String, eventNumber: Long, timestamp: Instant, sk: SessionKey, message: String)

  // Exceptions
  case class ChannelNotJoinedException(channelId: String) extends Exception()
  case class ChannelAlreadyJoinedException(channelId: String) extends Exception()
  case class ChannelNotFoundException(channelId: String) extends Exception()
  case class ChannelAlreadyExistsException(channelId: String) extends Exception()

  object ChatChannelException {
    def apply(t: Throwable): Boolean = t match {
      case _: ChannelNotJoinedException | _: ChannelAlreadyJoinedException | _: ChannelNotFoundException | _: ChannelAlreadyExistsException => true
      case _ => false
    }
    def unapply(t: Throwable): Option[Throwable] = if (apply(t)) Some(t) else None
  }
}