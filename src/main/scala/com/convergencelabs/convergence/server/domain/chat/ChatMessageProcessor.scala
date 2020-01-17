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

package com.convergencelabs.convergence.server.domain.chat

import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatMessages._

import scala.util.Try

/**
 * This class is a delegate for handling messages to delivered to a
 * [[ChatActor]].
 *
 * @param stateManager The state manager that will persist chat state.
 */
private[chat] abstract class ChatMessageProcessor(stateManager: ChatStateManager) {

  def processChatMessage(message: ExistingChatMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case message: RemoveChatRequest =>
        onRemoveChannel(message)
      case message: JoinChannelRequest =>
        onJoinChannel(message)
      case message: LeaveChannelRequest =>
        onLeaveChannel(message)
      case message: AddUserToChannelRequest =>
        onAddUserToChannel(message)
      case message: RemoveUserFromChannelRequest =>
        onRemoveUserFromChannel(message)
      case message: SetChatNameRequest =>
        onSetChatChannelName(message)
      case message: SetChatTopicRequest =>
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

  def broadcast(message: Any): Unit

  protected def onJoinChannel(message: JoinChannelRequest): Try[ChatMessageProcessingResult] = {
    val JoinChannelRequest(_, _, requester, _) = message
    stateManager.onJoinChannel(requester.userId) map {
      case ChatUserJoinedEvent(eventNo, chatId, user, timestamp) =>
        ChatMessageProcessingResult(
          Some(createJoinResponse()),
          List(UserJoinedChat(chatId, eventNo, timestamp, user)))
    }
  }

  protected def onLeaveChannel(message: LeaveChannelRequest): Try[ChatMessageProcessingResult] = {
    val LeaveChannelRequest(_, _, requester, _) = message
    stateManager.onLeaveChannel(requester.userId) map {
      case ChatUserLeftEvent(eventNo, chatId, user, timestamp) =>
        ChatMessageProcessingResult(
          Some(()),
          List(UserLeftChat(chatId, eventNo, timestamp, user)))
    }
  }

  protected def onAddUserToChannel(message: AddUserToChannelRequest): Try[ChatMessageProcessingResult] = {
    val AddUserToChannelRequest(_, chatId, requester, userToAdd) = message
    stateManager.onAddUserToChannel(chatId, requester.userId, userToAdd) map {
      case ChatUserAddedEvent(eventNo, chatId, user, timestamp, addedUserId) =>
        ChatMessageProcessingResult(Some(()), List(UserAddedToChannel(chatId, eventNo, timestamp, user, addedUserId)))
    }
  }

  protected def onRemoveUserFromChannel(message: RemoveUserFromChannelRequest): Try[ChatMessageProcessingResult] = {
    val RemoveUserFromChannelRequest(_, chatId, requester, userToRemove) = message
    stateManager.onRemoveUserFromChannel(chatId, requester.userId, userToRemove) map {
      case ChatUserRemovedEvent(eventNo, chatId, user, timestamp, removedUserId) =>
        ChatMessageProcessingResult(Some(()), List(UserAddedToChannel(chatId, eventNo, timestamp, user, removedUserId)))
    }
  }

  protected def onSetChatChannelName(message: SetChatNameRequest): Try[ChatMessageProcessingResult] = {
    val SetChatNameRequest(_, chatId, requester, name) = message
    stateManager.onSetChatChannelName(chatId, requester, name) map {
      case ChatNameChangedEvent(eventNo, chatId, user, timestamp, name) =>
        ChatMessageProcessingResult(Some(()), List(ChatNameChanged(chatId, eventNo, timestamp, user, name)))
    }
  }

  protected  def onSetChatChannelTopic(message: SetChatTopicRequest): Try[ChatMessageProcessingResult] = {
    val SetChatTopicRequest(_, chatId, requester, topic) = message
    stateManager.onSetChatChannelTopic(chatId, requester, topic) map {
      case ChatTopicChangedEvent(eventNo, chatId, user, timestamp, topic) =>
        ChatMessageProcessingResult(Some(()), List(ChatTopicChanged(chatId, eventNo, timestamp, user, topic)))
    }
  }

  protected def onMarkEventsSeen(message: MarkChannelEventsSeenRequest): Try[ChatMessageProcessingResult] = {
    val MarkChannelEventsSeenRequest(_, chatId, requester, eventNumber) = message
    stateManager.onMarkEventsSeen(chatId, requester.userId, eventNumber) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  protected def onGetHistory(message: GetChannelHistoryRequest): Try[ChatMessageProcessingResult] = {
    val GetChannelHistoryRequest(_, chatId, requester, limit, startEvent, forward, eventFilter) = message
    stateManager.onGetHistory(chatId, requester.userId, limit, startEvent, forward, eventFilter) map { events =>
      ChatMessageProcessingResult(Some(GetChannelHistoryResponse(events)), List())
    }
  }

  protected def onPublishMessage(message: PublishChatMessageRequest): Try[ChatMessageProcessingResult] = {
    val PublishChatMessageRequest(_, chatId, requester, msg) = message
    stateManager.onPublishMessage(chatId, requester.userId, msg) map {
      case ChatMessageEvent(eventNo, chatId, _, timestamp, msg) =>
        ChatMessageProcessingResult(Some(()), List(RemoteChatMessage(chatId, eventNo, timestamp, requester, msg)))
    }
  }

  protected def onRemoveChannel(message: RemoveChatRequest): Try[ChatMessageProcessingResult] = {
    val RemoveChatRequest(_, chatId, requester) = message
    stateManager.onRemoveChannel(chatId, requester) map { _ =>
      ChatMessageProcessingResult(Some(()), List(ChannelRemoved(chatId)))
    }
  }

  protected def onAddPermissionsMessage(message: AddChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val AddChatPermissionsRequest(_, chatId, requester, world, user, group) = message
    stateManager.onAddPermissions(chatId, requester.userId, world, user, group) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  protected def onRemovePermissionsMessage(message: RemoveChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val RemoveChatPermissionsRequest(_, chatId, requester, world, user, group) = message
    stateManager.onRemovePermissions(chatId, requester.userId, world, user, group) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  protected def onSetPermissionsMessage(message: SetChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val SetChatPermissionsRequest(_, chatId, requester, world, user, group) = message
    stateManager.onSetPermissions(chatId, requester.userId, world, user, group) map { _ =>
      ChatMessageProcessingResult(Some(()), List())
    }
  }

  protected def onGetClientPermissions(message: GetClientChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetClientChatPermissionsRequest(_, chatId, requester) = message
    stateManager.onGetClientPermissions(chatId, requester.userId) map { permissions =>
      ChatMessageProcessingResult(Some(GetClientChatPermissionsResponse(permissions)), List())
    }
  }

  protected def onGetWorldPermissions(message: GetWorldChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetWorldChatPermissionsRequest(_, chatId, _) = message
    stateManager.onGetWorldPermissions(chatId) map { permissions =>
      ChatMessageProcessingResult(Some(GetWorldChatPermissionsResponse(permissions)), List())
    }
  }

  protected def onGetAllUserPermissions(message: GetAllUserChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetAllUserChatPermissionsRequest(_, chatId, _) = message
    stateManager.onGetAllUserPermissions(chatId) map { permissions =>
      val map = permissions.groupBy { _.user } map { case (user, userPermissions) => DomainUserId(user.userType, user.username) -> userPermissions.map { _.permission } }
      ChatMessageProcessingResult(Some(GetAllUserChatPermissionsResponse(map)), List())
    }
  }

  protected def onGetAllGroupPermissions(message: GetAllGroupChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetAllGroupChatPermissionsRequest(_, chatId, _) = message
    stateManager.onGetAllGroupPermissions(chatId) map { permissions =>
      val map = permissions.groupBy { _.group } map { case (group, groupPermissions) => group.id -> groupPermissions.map { _.permission } }
      ChatMessageProcessingResult(Some(GetAllGroupChatPermissionsResponse(map)), List())
    }
  }

  protected def onGetUserPermissions(message: GetUserChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetUserChatPermissionsRequest(_, chatId, requester, _) = message
    stateManager.onGetUserPermissions(chatId, requester.userId) map { permissions =>
      ChatMessageProcessingResult(Some(GetUserChatPermissionsResponse(permissions)), List())
    }
  }

  protected def onGetGroupPermissions(message: GetGroupChatPermissionsRequest): Try[ChatMessageProcessingResult] = {
    val GetGroupChatPermissionsRequest(_, chatId, _, groupId) = message
    stateManager.onGetGroupPermissions(chatId, groupId) map { permissions =>
      ChatMessageProcessingResult(Some(GetGroupChatPermissionsResponse(permissions)), List())
    }
  }

  protected def createJoinResponse(): JoinChannelResponse = {
    val ChatChannelState(id, chatType, created, isPrivate, name, topic, lastEventTime, lastEventNo, members) = stateManager.state()
    val info = ChatInfo(id, chatType, created, isPrivate, name, topic, lastEventNo, lastEventTime, members.values.toSet)
    JoinChannelResponse(info)
  }
}

private[chat] case class ChatMessageProcessingResult(response: Option[Any], broadcastMessages: List[Any])
