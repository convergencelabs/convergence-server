package com.convergencelabs.server.domain.chat

import scala.util.Try

import com.convergencelabs.server.datastore.domain.ChatInfo
import com.convergencelabs.server.datastore.domain.ChatMessageEvent
import com.convergencelabs.server.datastore.domain.ChatNameChangedEvent
import com.convergencelabs.server.datastore.domain.ChatTopicChangedEvent
import com.convergencelabs.server.datastore.domain.ChatUserAddedEvent
import com.convergencelabs.server.datastore.domain.ChatUserJoinedEvent
import com.convergencelabs.server.datastore.domain.ChatUserLeftEvent
import com.convergencelabs.server.datastore.domain.ChatUserRemovedEvent
import com.convergencelabs.server.domain.chat.ChatMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.ChatNameChanged
import com.convergencelabs.server.domain.chat.ChatMessages.ChatTopicChanged
import com.convergencelabs.server.domain.chat.ChatMessages.ExistingChatMessage
import com.convergencelabs.server.domain.chat.ChatMessages.GetChannelHistoryRequest
import com.convergencelabs.server.domain.chat.ChatMessages.GetChannelHistoryResponse
import com.convergencelabs.server.domain.chat.ChatMessages.JoinChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.JoinChannelResponse
import com.convergencelabs.server.domain.chat.ChatMessages.LeaveChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.MarkChannelEventsSeenRequest
import com.convergencelabs.server.domain.chat.ChatMessages.PublishChatMessageRequest
import com.convergencelabs.server.domain.chat.ChatMessages.RemoteChatMessage
import com.convergencelabs.server.domain.chat.ChatMessages.RemoveChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.RemoveUserFromChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.SetChannelNameRequest
import com.convergencelabs.server.domain.chat.ChatMessages.SetChannelTopicRequest
import com.convergencelabs.server.domain.chat.ChatMessages.UserAddedToChannel
import com.convergencelabs.server.domain.chat.ChatMessages.UserJoinedChat
import com.convergencelabs.server.domain.chat.ChatMessages.AddChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatMessages.RemoveChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatMessages.SetChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatMessages.GetClientChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatMessages.GetClientChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatMessages.GetWorldChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatMessages.GetWorldChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatMessages.GetAllUserChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatMessages.GetAllUserChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatMessages.GetAllGroupChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatMessages.GetAllGroupChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatMessages.GetUserChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatMessages.GetUserChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatMessages.GetGroupChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatMessages.GetGroupChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatMessages.UserLeftChat
import com.convergencelabs.server.domain.chat.ChatMessages.ChannelRemoved
import com.convergencelabs.server.domain.DomainUserId

case class ChatMessageProcessingResult(response: Option[Any], broadcastMessages: List[Any])

abstract class ChatMessageProcessor(stateManager: ChatStateManager) {

  def processChatMessage(message: ExistingChatMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case message: RemoveChannelRequest =>
        onRemoveChannel(message)
      case message: JoinChannelRequest =>
        onJoinChannel(message)
      case message: LeaveChannelRequest =>
        onLeaveChannel(message)
      case message: AddUserToChannelRequest =>
        onAddUserToChannel(message)
      case message: RemoveUserFromChannelRequest =>
        onRemoveUserFromChannel(message)
      case message: SetChannelNameRequest =>
        onSetChatChannelName(message)
      case message: SetChannelTopicRequest =>
        onSetChatChannelTopic(message)
      case message: MarkChannelEventsSeenRequest =>
        onMarkEventsSeen(message)
      case message: GetChannelHistoryRequest =>
        onGetHistory(message)
      case message: PublishChatMessageRequest =>
        onPublishMessage(message)
      case message: AddChatPermissionsRequest =>
        onAddPermissionsMessage(message)
      case message: RemoveChatPermissionsRequest =>
        onRemovePermissionsMessage(message)
      case message: SetChatPermissionsRequest =>
        onSetPermissionsMessage(message)
      case message: GetClientChatPermissionsRequest =>
        onGetClientPermissions(message)
      case message: GetWorldChatPermissionsRequest =>
        onGetWorldPermissions(message)
      case message: GetAllUserChatPermissionsRequest =>
        onGetAllUserPermissions(message)
      case message: GetAllGroupChatPermissionsRequest =>
        onGetAllGroupPermissions(message)
      case message: GetUserChatPermissionsRequest =>
        onGetUserPermissions(message)
      case message: GetGroupChatPermissionsRequest =>
        onGetGroupPermissions(message)
    }
  }

  def onJoinChannel(message: JoinChannelRequest): Try[ChatMessageProcessingResult] = {
    val JoinChannelRequest(domainFqn, chatId, requestor, client) = message
    stateManager.onJoinChannel(requestor.userId) map {
      case ChatUserJoinedEvent(eventNo, chatId, user, timestamp) =>
        ChatMessageProcessingResult(
          Some(createJoinResponse()),
          List(UserJoinedChat(chatId, eventNo, timestamp, user)))
    }
  }

  def onLeaveChannel(message: LeaveChannelRequest): Try[ChatMessageProcessingResult] = {
    val LeaveChannelRequest(domainFqn, chatId, requestor, client) = message
    stateManager.onLeaveChannel(requestor.userId) map {
      case ChatUserLeftEvent(eventNo, chatId, user, timestamp) =>
        ChatMessageProcessingResult(
          Some(()),
          List(UserLeftChat(chatId, eventNo, timestamp, user)))
    }
  }

  def onAddUserToChannel(message: AddUserToChannelRequest): Try[ChatMessageProcessingResult] = {
    val AddUserToChannelRequest(domainFqn, chatId, requestor, userToAdd) = message;
    stateManager.onAddUserToChannel(chatId, requestor.userId, userToAdd) map {
      case ChatUserAddedEvent(eventNo, chatId, user, timestamp, addedUserId) =>
        ChatMessageProcessingResult(Some(()), List(UserAddedToChannel(chatId, eventNo, timestamp, user, addedUserId)))
    }
  }

  def onRemoveUserFromChannel(message: RemoveUserFromChannelRequest): Try[ChatMessageProcessingResult] = {
    val RemoveUserFromChannelRequest(domainFqn, chatId, requestor, userToRemove) = message;
    stateManager.onRemoveUserFromChannel(chatId, requestor.userId, userToRemove) map {
      case ChatUserRemovedEvent(eventNo, chatId, user, timestamp, removedUserId) =>
        ChatMessageProcessingResult(Some(()), List(UserAddedToChannel(chatId, eventNo, timestamp, user, removedUserId)))
    }
  }

  def onSetChatChannelName(message: SetChannelNameRequest): Try[ChatMessageProcessingResult] = {
    val SetChannelNameRequest(domainFqn, chatId, requestor, name) = message;
    stateManager.onSetChatChannelName(chatId, requestor.userId, name) map {
      case ChatNameChangedEvent(eventNo, chatId, user, timestamp, name) =>
        ChatMessageProcessingResult(Some(()), List(ChatNameChanged(chatId, eventNo, timestamp, user, name)))
    }
  }

  def onSetChatChannelTopic(message: SetChannelTopicRequest): Try[ChatMessageProcessingResult] = {
    val SetChannelTopicRequest(domainFqn, chatId, requestor, topic) = message;
    stateManager.onSetChatChannelTopic(chatId, requestor.userId, topic) map {
      case ChatTopicChangedEvent(eventNo, chatId, user, timestamp, topic) =>
        ChatMessageProcessingResult(Some(()), List(ChatTopicChanged(chatId, eventNo, timestamp, user, topic)))
    }
  }

  def onMarkEventsSeen(message: MarkChannelEventsSeenRequest): Try[ChatMessageProcessingResult] = {
    val MarkChannelEventsSeenRequest(domainFqn, chatId, requestor, eventNumber) = message;
    stateManager.onMarkEventsSeen(chatId, requestor.userId, eventNumber) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  def onGetHistory(message: GetChannelHistoryRequest): Try[ChatMessageProcessingResult] = {
    val GetChannelHistoryRequest(domainFqn, chatId, requestor, limit, startEvent, forward, eventFilter) = message;
    stateManager.onGetHistory(chatId, requestor.userId, limit, startEvent, forward, eventFilter) map { events =>
      ChatMessageProcessingResult(Some(GetChannelHistoryResponse(events)), List())
    }
  }

  def onPublishMessage(message: PublishChatMessageRequest): Try[ChatMessageProcessingResult] = {
    val PublishChatMessageRequest(domainFqn, chatId, requestor, msg) = message;
    stateManager.onPublishMessage(chatId, requestor.userId, msg) map {
      case ChatMessageEvent(eventNo, chatId, user, timestamp, msg) =>
        ChatMessageProcessingResult(Some(()), List(RemoteChatMessage(chatId, eventNo, timestamp, requestor, msg)))
    }
  }

  def onRemoveChannel(message: RemoveChannelRequest): Try[ChatMessageProcessingResult] = {
    val RemoveChannelRequest(domainFqn, chatId, requestor) = message;
    stateManager.onRemoveChannel(chatId, requestor.userId) map { _ =>
      ChatMessageProcessingResult(Some(()), List(ChannelRemoved(chatId)))
    }
  }

  def onAddPermissionsMessage(message: AddChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val AddChatPermissionsRequest(domainFqn, chatId, requestor, world, user, group) = message;
    stateManager.onAddPermissions(chatId, requestor.userId, world, user, group) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  def onRemovePermissionsMessage(message: RemoveChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val RemoveChatPermissionsRequest(domainFqn, chatId, requestor, world, user, group) = message;
    stateManager.onRemovePermissions(chatId, requestor.userId, world, user, group) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  def onSetPermissionsMessage(message: SetChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val SetChatPermissionsRequest(domainFqn, chatId, requestor, world, user, group) = message;
    stateManager.onSetPermissions(chatId, requestor.userId, world, user, group) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  def onGetClientPermissions(message: GetClientChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetClientChatPermissionsRequest(domainFqn, chatId, requestor) = message;
    stateManager.onGetClientPermissions(chatId, requestor.userId) map { permissions =>
      ChatMessageProcessingResult(Some(GetClientChatPermissionsResponse(permissions)), List())
    }
  }

  def onGetWorldPermissions(message: GetWorldChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetWorldChatPermissionsRequest(domainFqn, chatId, requestor) = message;
    stateManager.onGetWorldPermissions(chatId) map { permissions =>
      ChatMessageProcessingResult(Some(GetWorldChatPermissionsResponse(permissions)), List())
    }
  }

  def onGetAllUserPermissions(message: GetAllUserChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetAllUserChatPermissionsRequest(domainFqn, chatId, requestor) = message;
    stateManager.onGetAllUserPermissions(chatId) map { permissions =>
      val map = permissions.groupBy { _.user } map { case (user, userPermissions) => (DomainUserId(user.userType, user.username) -> userPermissions.map { _.permission }) }
      ChatMessageProcessingResult(Some(GetAllUserChatPermissionsResponse(map)), List())
    }
  }

  def onGetAllGroupPermissions(message: GetAllGroupChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetAllGroupChatPermissionsRequest(domainFqn, chatId, requestor) = message;
    stateManager.onGetAllGroupPermissions(chatId) map { permissions =>
      val map = permissions.groupBy { _.group } map { case (group, groupPermissions) => (group.id -> groupPermissions.map { _.permission }) }
      ChatMessageProcessingResult(Some(GetAllGroupChatPermissionsResponse(map)), List())
    }
  }

  def onGetUserPermissions(message: GetUserChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetUserChatPermissionsRequest(domainFqn, chatId, requestor, userId) = message;
    stateManager.onGetUserPermissions(chatId, requestor.userId) map { permissions =>
      ChatMessageProcessingResult(Some(GetUserChatPermissionsResponse(permissions)), List())
    }
  }

  def onGetGroupPermissions(message: GetGroupChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetGroupChatPermissionsRequest(domainFqn, chatId, requestor, groupId) = message;
    stateManager.onGetGroupPermissions(chatId, groupId) map { permissions =>
      ChatMessageProcessingResult(Some(GetGroupChatPermissionsResponse(permissions)), List())
    }
  }

  def createJoinResponse(): JoinChannelResponse = {
    val ChatChannelState(id, chatType, created, isPrivate, name, topic, lastEventTime, lastEventNo, members) = stateManager.state()
    val info = ChatInfo(id, chatType, created, isPrivate, name, topic, lastEventNo, lastEventTime, members.values.toSet)
    JoinChannelResponse(info)
  }

  def boradcast(message: Any): Unit
}
