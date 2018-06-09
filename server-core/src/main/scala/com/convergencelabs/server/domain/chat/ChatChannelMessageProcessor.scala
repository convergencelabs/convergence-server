package com.convergencelabs.server.domain.chat

import scala.util.Try

import com.convergencelabs.server.datastore.domain.ChatChannelInfo
import com.convergencelabs.server.datastore.domain.ChatMessageEvent
import com.convergencelabs.server.datastore.domain.ChatNameChangedEvent
import com.convergencelabs.server.datastore.domain.ChatTopicChangedEvent
import com.convergencelabs.server.datastore.domain.ChatUserAddedEvent
import com.convergencelabs.server.datastore.domain.ChatUserJoinedEvent
import com.convergencelabs.server.datastore.domain.ChatUserLeftEvent
import com.convergencelabs.server.datastore.domain.ChatUserRemovedEvent
import com.convergencelabs.server.domain.chat.ChatChannelMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelNameChanged
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelTopicChanged
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ExistingChannelMessage
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetChannelHistoryRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetChannelHistoryResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.JoinChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.JoinChannelResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.LeaveChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.MarkChannelEventsSeenRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.PublishChatMessageRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoteChatMessage
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoveChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoveUserFromChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.SetChannelNameRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.SetChannelTopicRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.UserAddedToChannel
import com.convergencelabs.server.domain.chat.ChatChannelMessages.UserJoinedChannel
import com.convergencelabs.server.frontend.realtime.ChatChannelRemovedMessage
import com.convergencelabs.server.domain.chat.ChatChannelMessages.AddChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoveChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.SetChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetClientChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetClientChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetWorldChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetWorldChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetAllUserChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetAllUserChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetAllGroupChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetAllGroupChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetUserChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetUserChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetGroupChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetGroupChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.UserLeftChannel

case class ChatMessageProcessingResult(response: Option[Any], broadcastMessages: List[Any])

abstract class ChatChannelMessageProcessor(stateManager: ChatChannelStateManager) {

  def processChatMessage(message: ExistingChannelMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case message: RemoveChannelRequest              => onRemoveChannel(message)
      case message: JoinChannelRequest                => onJoinChannel(message)
      case message: LeaveChannelRequest               => onLeaveChannel(message)
      case message: AddUserToChannelRequest           => onAddUserToChannel(message)
      case message: RemoveUserFromChannelRequest      => onRemoveUserFromChannel(message)
      case message: SetChannelNameRequest             => onSetChatChannelName(message)
      case message: SetChannelTopicRequest            => onSetChatChannelTopic(message)
      case message: MarkChannelEventsSeenRequest      => onMarkEventsSeen(message)
      case message: GetChannelHistoryRequest          => onGetHistory(message)
      case message: PublishChatMessageRequest         => onPublishMessage(message)
      case message: AddChatPermissionsRequest         => onAddPermissionsMessage(message)
      case message: RemoveChatPermissionsRequest      => onRemovePermissionsMessage(message)
      case message: SetChatPermissionsRequest         => onSetPermissionsMessage(message)
      case message: GetClientChatPermissionsRequest   => onGetClientPermissions(message)
      case message: GetWorldChatPermissionsRequest    => onGetWorldPermissions(message)
      case message: GetAllUserChatPermissionsRequest  => onGetAllUserPermissions(message)
      case message: GetAllGroupChatPermissionsRequest => onGetAllGroupPermissions(message)
      case message: GetUserChatPermissionsRequest     => onGetUserPermissions(message)
      case message: GetGroupChatPermissionsRequest    => onGetGroupPermissions(message)
    }
  }

  def onJoinChannel(message: JoinChannelRequest): Try[ChatMessageProcessingResult] = {
    val JoinChannelRequest(domainFqn, channelId, sk, client) = message
    stateManager.onJoinChannel(sk) map {
      case ChatUserJoinedEvent(eventNo, channelId, username, timestamp) =>
        ChatMessageProcessingResult(
          Some(createJoinResponse()),
          List(UserJoinedChannel(channelId, eventNo, timestamp, username)))
    }
  }

  def onLeaveChannel(message: LeaveChannelRequest): Try[ChatMessageProcessingResult] = {
    val LeaveChannelRequest(domainFqn, channelId, sk, client) = message
    stateManager.onLeaveChannel(sk) map {
      case ChatUserLeftEvent(eventNo, channelId, username, timestamp) =>
        ChatMessageProcessingResult(
          Some(()),
          List(UserLeftChannel(channelId, eventNo, timestamp, username)))
    }
  }

  def onAddUserToChannel(message: AddUserToChannelRequest): Try[ChatMessageProcessingResult] = {
    val AddUserToChannelRequest(domainFqn, channelId, sk, username) = message;
    stateManager.onAddUserToChannel(channelId, sk, username) map {
      case ChatUserAddedEvent(eventNo, channelId, addedBy, timestamp, username) =>
        ChatMessageProcessingResult(Some(()), List(UserAddedToChannel(channelId, eventNo, timestamp, username, addedBy)))
    }
  }

  def onRemoveUserFromChannel(message: RemoveUserFromChannelRequest): Try[ChatMessageProcessingResult] = {
    val RemoveUserFromChannelRequest(domainFqn, channelId, sk, username) = message;
    stateManager.onRemoveUserFromChannel(channelId, sk, username) map {
      case ChatUserRemovedEvent(eventNo, channelId, removedBy, timestamp, username) =>
        ChatMessageProcessingResult(Some(()), List(UserAddedToChannel(channelId, eventNo, timestamp, username, removedBy)))
    }
  }

  def onSetChatChannelName(message: SetChannelNameRequest): Try[ChatMessageProcessingResult] = {
    val SetChannelNameRequest(domainFqn, channelId, sk, name) = message;
    stateManager.onSetChatChannelName(channelId, sk, name) map {
      case ChatNameChangedEvent(eventNo, channelId, setBy, timestamp, topic) =>
        ChatMessageProcessingResult(Some(()), List(ChannelNameChanged(channelId, eventNo, timestamp, setBy, topic)))
    }
  }

  def onSetChatChannelTopic(message: SetChannelTopicRequest): Try[ChatMessageProcessingResult] = {
    val SetChannelTopicRequest(domainFqn, channelId, sk, topic) = message;
    stateManager.onSetChatChannelTopic(channelId, sk, topic) map {
      case ChatTopicChangedEvent(eventNo, channelId, setBy, timestamp, topic) =>
        ChatMessageProcessingResult(Some(()), List(ChannelTopicChanged(channelId, eventNo, timestamp, setBy, topic)))
    }
  }

  def onMarkEventsSeen(message: MarkChannelEventsSeenRequest): Try[ChatMessageProcessingResult] = {
    val MarkChannelEventsSeenRequest(domainFqn, channelId, sk, eventNumber) = message;
    stateManager.onMarkEventsSeen(channelId, sk, eventNumber) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  def onGetHistory(message: GetChannelHistoryRequest): Try[ChatMessageProcessingResult] = {
    val GetChannelHistoryRequest(domainFqn, channelId, sk, limit, offset, forward, eventFilter) = message;
    stateManager.onGetHistory(channelId, sk.uid, limit, offset, forward, eventFilter) map { events =>
      ChatMessageProcessingResult(Some(GetChannelHistoryResponse(events)), List())
    }
  }

  def onPublishMessage(message: PublishChatMessageRequest): Try[ChatMessageProcessingResult] = {
    val PublishChatMessageRequest(domainFqn, channelId, sk, msg) = message;
    stateManager.onPublishMessage(channelId, sk, msg) map {
      case ChatMessageEvent(eventNo, channelId, uid, timestamp, msg) =>
        ChatMessageProcessingResult(Some(()), List(RemoteChatMessage(channelId, eventNo, timestamp, sk, msg)))
    }
  }

  def onRemoveChannel(message: RemoveChannelRequest): Try[ChatMessageProcessingResult] = {
    val RemoveChannelRequest(domainFqn, channelId, sk) = message;
    stateManager.onRemoveChannel(channelId, sk) map { _ =>
      ChatMessageProcessingResult(Some(()), List(ChatChannelRemovedMessage(channelId)))
    }
  }

  def onAddPermissionsMessage(message: AddChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val AddChatPermissionsRequest(domainFqn, channelId, sk, world, user, group) = message;
    stateManager.onAddPermissions(channelId, sk, world, user, group) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  def onRemovePermissionsMessage(message: RemoveChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val RemoveChatPermissionsRequest(domainFqn, channelId, sk, world, user, group) = message;
    stateManager.onRemovePermissions(channelId, sk, world, user, group) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  def onSetPermissionsMessage(message: SetChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val SetChatPermissionsRequest(domainFqn, channelId, sk, world, user, group) = message;
    stateManager.onSetPermissions(channelId, sk, world, user, group) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  def onGetClientPermissions(message: GetClientChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetClientChatPermissionsRequest(domainFqn, channelId, sk) = message;
    stateManager.onGetClientPermissions(channelId, sk) map { permissions =>
      ChatMessageProcessingResult(Some(GetClientChatPermissionsResponse(permissions)), List())
    }
  }

  def onGetWorldPermissions(message: GetWorldChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetWorldChatPermissionsRequest(domainFqn, channelId, sk) = message;
    stateManager.onGetWorldPermissions(channelId, sk) map { permissions =>
      ChatMessageProcessingResult(Some(GetWorldChatPermissionsResponse(permissions)), List())
    }
  }

  def onGetAllUserPermissions(message: GetAllUserChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetAllUserChatPermissionsRequest(domainFqn, channelId, sk) = message;
    stateManager.onGetAllUserPermissions(channelId, sk) map { permissions =>
      val map = permissions.groupBy { _.user } map { case (user, userPermissions) => (user.username -> userPermissions.map { _.permission }) }
      ChatMessageProcessingResult(Some(GetAllUserChatPermissionsResponse(map)), List())
    }
  }

  def onGetAllGroupPermissions(message: GetAllGroupChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetAllGroupChatPermissionsRequest(domainFqn, channelId, sk) = message;
    stateManager.onGetAllGroupPermissions(channelId, sk) map { permissions =>
      val map = permissions.groupBy { _.group } map { case (group, groupPermissions) => (group.id -> groupPermissions.map { _.permission }) }
      ChatMessageProcessingResult(Some(GetAllGroupChatPermissionsResponse(map)), List())
    }
  }

  def onGetUserPermissions(message: GetUserChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetUserChatPermissionsRequest(domainFqn, channelId, username, sk) = message;
    stateManager.onGetUserPermissions(channelId, username, sk) map { permissions =>
      ChatMessageProcessingResult(Some(GetUserChatPermissionsResponse(permissions)), List())
    }
  }

  def onGetGroupPermissions(message: GetGroupChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetGroupChatPermissionsRequest(domainFqn, channelId, groupId, sk) = message;
    stateManager.onGetGroupPermissions(channelId, groupId, sk) map { permissions =>
      ChatMessageProcessingResult(Some(GetGroupChatPermissionsResponse(permissions)), List())
    }
  }

  def createJoinResponse(): JoinChannelResponse = {
    val ChatChannelState(id, channelType, created, isPrivate, name, topic, lastEventTime, lastEventNo, members) = stateManager.state()
    val info = ChatChannelInfo(id, channelType, created, isPrivate, name, topic, members, lastEventNo, lastEventTime)
    JoinChannelResponse(info)
  }

  def boradcast(message: Any): Unit
}
