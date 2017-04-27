package com.convergencelabs.server.domain

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider

case class ChatMessageProcessingResult(response: Option[Any], broadcastMessages: List[Any], state: Option[ChatChannelState])

class ChatChannelManager(
    private[this] val channelId: String,
    private[this] val persistence: DomainPersistenceProvider) {
  import ChatChannelMessages._

  def handleChatMessage(message: ChatChannelMessage, state: Option[ChatChannelState]): Try[ChatMessageProcessingResult] = {
    (message match {
      case message: CreateChannelRequest =>
        onCreateChannel(message)
      case other: ExistingChannelMessage =>
        assertChannelExists(state) flatMap { state =>
          other match {
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
            case message: ChannelHistoryRequest =>
              onGetHistory(message, state)
            case message: PublishChatMessageRequest =>
              onPublishMessage(message, state)
          }
        }
    })
  }

  def onCreateChannel(message: CreateChannelRequest): Try[ChatMessageProcessingResult] = {
    val CreateChannelRequest(channelId, channelType, channelMembership, name, topic, members) = message;
    ???
  }

  def onRemoveChannel(message: RemoveChannelRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val RemoveChannelRequest(channelId, username) = message;
    ???
  }

  def onJoinChannel(message: JoinChannelRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val JoinChannelRequest(channelId, username) = message;
    val members = state.members
    if (members contains username) {
      Failure(ChannelAlreadyJoinedException(channelId))
    } else {
      val newMembers = members + username
      val newState = state.copy(members = newMembers)
      // TODO need help function to set new event number and last event time
      
      // update the database, potentially, we could do this async.
      val eventNo = state.lastEventNumber
      val time = state.lastEventTime
      
      Success(ChatMessageProcessingResult(Some(()), List(UserJoinedChannel(channelId, eventNo, time, username)), Some(state)))
    }
  }

  def onLeaveChannel(message: LeaveChannelRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val LeaveChannelRequest(channelId, username) = message;
    ???
  }

  def onAddUserToChannel(message: AddUserToChannelRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val AddUserToChannelRequest(channelId, username, addedBy) = message;
    ???
  }

  def onRemoveUserFromChannel(message: RemoveUserFromChannelRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val RemoveUserFromChannelRequest(channelId, username, removedBy) = message;
    ???
  }

  def onSetChatChannelName(message: SetChannelNameRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val SetChannelNameRequest(channelId, name, setBy) = message;
    ???
  }

  def onSetChatChannelTopic(message: SetChannelTopicRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val SetChannelTopicRequest(channelId, topic, setBy) = message;
    ???
  }

  def onMarkEventsSeen(message: MarkChannelEventsSeenRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val MarkChannelEventsSeenRequest(channelId, eventNumber, username) = message;
    ???
  }

  def onGetHistory(message: ChannelHistoryRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val ChannelHistoryRequest(username, channleId, limit, offset, forward, events) = message;
    ???
  }

  def onPublishMessage(message: PublishChatMessageRequest, state: ChatChannelState): Try[ChatMessageProcessingResult] = {
    val PublishChatMessageRequest(sk, channeId, msg) = message;
    ???
  }

  private def assertChannelExists(state: Option[ChatChannelState]): Try[ChatChannelState] = {
    state match {
      case Some(state) => Success(state)
      case None => Failure(ChannelNotFoundException(channelId))
    }
  }
}