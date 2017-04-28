package com.convergencelabs.server.domain

import java.time.Instant

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.domain.ChatChannelStore
import com.convergencelabs.server.datastore.domain.ChatMessageEvent
import com.convergencelabs.server.datastore.domain.ChatNameChangedEvent
import com.convergencelabs.server.datastore.domain.ChatTopicChangedEvent
import com.convergencelabs.server.datastore.domain.ChatUserAddedEvent
import com.convergencelabs.server.datastore.domain.ChatUserJoinedEvent
import com.convergencelabs.server.datastore.domain.ChatUserLeftEvent
import com.convergencelabs.server.datastore.domain.ChatUserRemovedEvent
import com.convergencelabs.server.domain.ChatChannelMessages.ChannelNotFoundException
import com.convergencelabs.server.frontend.realtime.ChatChannelRemovedMessage

case class ChatMessageProcessingResult(response: Option[Any], broadcastMessages: List[Any], state: Option[ChatChannelState])

object ChatChannelManager {
  def create(channelId: String, chatChannelStore: ChatChannelStore): Try[ChatChannelManager] = {
    
    (for {
      channel <- chatChannelStore.getChatChannel(channelId)
      members <- chatChannelStore.getChatChannelMembers(channelId)
      lastEvent <- chatChannelStore.getChatChannelEvents(channelId, Some(0), Some(1))
    } yield {
      val (maxEvent, lastTime) = lastEvent.headOption.map(event => (event.eventNo, event.timestamp)).getOrElse((0L, channel.created))

      val state = ChatChannelState(
        channelId,
        channel.channelType,
        channel.created,
        channel.isPrivate,
        channel.name,
        channel.topic,
        lastTime,
        maxEvent,
        members)
      
      new ChatChannelManager(channelId, state, chatChannelStore)
    })recoverWith {
      case cause: EntityNotFoundException =>
        Failure(ChannelNotFoundException(channelId))
    } 
  }
}

class ChatChannelManager(
    private[this] val channelId: String,
    private[this] var state: ChatChannelState,
    private[this] val channelStore: ChatChannelStore) {
  import ChatChannelMessages._

  def state(): ChatChannelState = {
    state
  }

  def handleChatMessage(message: ExistingChannelMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case message: RemoveChannelRequest =>
        onRemoveChannel(message, state)
      case message: JoinChannelRequest =>
        onJoinChannel(message, state)
      case message: LeaveChannelRequest =>
        onLeaveChannel(message, state)
      case message: AddUserToChannelRequest =>
        onAddUserToChannel(message, state)
      case message: RemoveUserFromChannelRequest =>
        onRemoveUserFromChannel(message, state)
      case message: SetChannelNameRequest =>
        onSetChatChannelName(message, state)
      case message: SetChannelTopicRequest =>
        onSetChatChannelTopic(message, state)
      case message: MarkChannelEventsSeenRequest =>
        onMarkEventsSeen(message, state)
      case message: GetChannelHistoryRequest =>
        onGetHistory(message, state)
      case message: PublishChatMessageRequest =>
        onPublishMessage(message, state)
    }
  }

  def onRemoveChannel(message: RemoveChannelRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val RemoveChannelRequest(channelId, username) = message;
    channelStore.removeChatChannel(channelId) map { _ =>
      ChatMessageProcessingResult(
        Some(()),
        List(ChatChannelRemovedMessage(channelId)),
        None)
    }
  }

  def onJoinChannel(message: JoinChannelRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val JoinChannelRequest(channelId, username) = message;
    val members = state.members
    if (members contains username) {
      Failure(ChannelAlreadyJoinedException(channelId))
    } else {
      val newMembers = members + username

      // TODO need help function to set new event number and last event time
      // update the database, potentially, we could do this async.
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatUserJoinedEvent(eventNo, channelId, username, timestamp)

      for {
        _ <- channelStore.addChatUserJoinedEvent(event)
        _ <- channelStore.addChatChannelMember(channelId, username, None)
      } yield {
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
        this.state = newState

        ChatMessageProcessingResult(
          Some(()),
          List(UserJoinedChannel(channelId, eventNo, timestamp, username)),
          Some(newState))
      }
    }
  }

  def onLeaveChannel(message: LeaveChannelRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val LeaveChannelRequest(channelId, username) = message;
    val members = state.members
    if (members contains username) {
      val newMembers = members - username
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatUserLeftEvent(eventNo, channelId, username, timestamp)

      for {
        _ <- channelStore.addChatUserLeftEvent(event)
        _ <- channelStore.removeChatChannelMember(channelId, username)
      } yield {
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
        this.state = newState

        ChatMessageProcessingResult(
          Some(()),
          List(UserLeftChannel(channelId, eventNo, timestamp, username)),
          Some(newState))
      }
    } else {
      Failure(ChannelNotJoinedException(channelId))
    }
  }

  def onAddUserToChannel(message: AddUserToChannelRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val AddUserToChannelRequest(channelId, username, addedBy) = message;
    val members = state.members
    if (members contains username) {
      Failure(ChannelAlreadyJoinedException(channelId))
    } else {
      val newMembers = members + username
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatUserAddedEvent(eventNo, channelId, addedBy, timestamp, username)
      for {
        _ <- channelStore.addChatUserAddedEvent(event)
        _ <- channelStore.addChatChannelMember(channelId, username, None)
      } yield {
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
        this.state = newState

        ChatMessageProcessingResult(
          Some(()),
          List(UserAddedToChannel(channelId, eventNo, timestamp, username, addedBy)),
          Some(newState))
      }
    }
  }

  def onRemoveUserFromChannel(message: RemoveUserFromChannelRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val RemoveUserFromChannelRequest(channelId, username, removedBy) = message;
    val members = state.members
    if (members contains username) {
      val newMembers = members - username
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatUserRemovedEvent(eventNo, channelId, removedBy, timestamp, username)

      for {
        _ <- channelStore.addChatUserRemovedEvent(event)
        _ <- channelStore.addChatChannelMember(channelId, username, None)
      } yield {
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
        this.state = newState

        ChatMessageProcessingResult(
          Some(()),
          List(UserRemovedFromChannel(channelId, eventNo, timestamp, username, removedBy)),
          Some(newState))
      }

    } else {
      Failure(ChannelNotJoinedException(channelId))
    }
  }

  def onSetChatChannelName(message: SetChannelNameRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val SetChannelNameRequest(channelId, name, username) = message;
    val eventNo = state.lastEventNumber + 1
    val timestamp = Instant.now()

    val event = ChatNameChangedEvent(eventNo, channelId, username, timestamp, name)

    for {
      _ <- channelStore.addChatNameChangedEvent(event)
      _ <- channelStore.updateChatChannel(channelId, Some(name), None)
    } yield {
      val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, name = name)
      this.state = newState

      ChatMessageProcessingResult(
        Some(()),
        List(ChannelNameChanged(channelId, eventNo, timestamp, username, name)),
        Some(newState))
    }
  }

  def onSetChatChannelTopic(message: SetChannelTopicRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val SetChannelTopicRequest(channelId, topic, username) = message;
    val eventNo = state.lastEventNumber + 1
    val timestamp = Instant.now()

    val event = ChatTopicChangedEvent(eventNo, channelId, username, timestamp, topic)

    for {
      _ <- channelStore.addChatTopicChangedEvent(event)
      _ <- channelStore.updateChatChannel(channelId, None, Some(topic))
    } yield {
      val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, topic = topic)
      this.state = newState
      ChatMessageProcessingResult(
        Some(()),
        List(ChannelTopicChanged(channelId, eventNo, timestamp, username, topic)),
        Some(newState))
    }
  }

  def onMarkEventsSeen(message: MarkChannelEventsSeenRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val MarkChannelEventsSeenRequest(channelId, eventNumber, username) = message;
    channelStore.markSeen(channelId, username, eventNumber) map { _ =>
      ChatMessageProcessingResult(
        Some(()),
        List(),
        None)
    }
  }

  def onGetHistory(message: GetChannelHistoryRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val GetChannelHistoryRequest(username, channleId, limit, offset, forward, events) = message;
    channelStore.getChatChannelEvents(channelId, offset, limit) map { events =>
      ChatMessageProcessingResult(
        Some(GetChannelHistoryResponse(events)),
        List(),
        None)
    }
  }

  def onPublishMessage(message: PublishChatMessageRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val PublishChatMessageRequest(channeId, msg, sk) = message;
    val eventNo = state.lastEventNumber + 1
    val timestamp = Instant.now()

    val event = ChatMessageEvent(eventNo, channelId, sk.uid, timestamp, msg)

    channelStore.addChatMessageEvent(event).map { _ =>
      val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp)
      this.state = newState

      ChatMessageProcessingResult(
        Some(()),
        List(RemoteChatMessage(channelId, eventNo, timestamp, sk, msg)),
        Some(newState))
    }
  }

  private def assertChannelExists(state: Option[ChatChannelState]): Try[ChatChannelState] = {
    state match {
      case Some(state) => Success(state)
      case None => Failure(ChannelNotFoundException(channelId))
    }
  }
}