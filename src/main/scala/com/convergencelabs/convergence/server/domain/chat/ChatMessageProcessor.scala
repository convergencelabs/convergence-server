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

import akka.actor.typed.ActorRef
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor._
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor._

import scala.util.Try

/**
 * This class is a delegate for handling messages to delivered to a
 * [[ChatActor]].
 *
 * @param stateManager The state manager that will persist chat state.
 */
private[chat] abstract class ChatMessageProcessor(stateManager: ChatStateManager) {

  def processChatMessage(message: ChatActor.RequestMessage): Try[ChatMessageProcessingResult[_]] = {
    message match {
      case message: RemoveChatRequest =>
        onRemoveChannel(message)
      case message: JoinChatRequest =>
        onJoinChannel(message)
      case message: LeaveChatRequest =>
        onLeaveChannel(message)
      case message: AddUserToChatRequest =>
        onAddUserToChannel(message)
      case message: RemoveUserFromChatRequest =>
        onRemoveUserFromChannel(message)
      case message: SetChatNameRequest =>
        onSetChatChannelName(message)
      case message: SetChatTopicRequest =>
        onSetChatChannelTopic(message)
      case message: MarkChatsEventsSeenRequest =>
        onMarkEventsSeen(message)
      case message: GetChatHistoryRequest =>
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

  def broadcast(message: ChatClientActor.OutgoingMessage): Unit


  protected def onJoinChannel(message: JoinChatRequest): Try[ChatMessageProcessingResult[_]] = {
    val JoinChatRequest(_, _, requester, _, replyTo) = message
    stateManager.onJoinChannel(requester.userId) map {
      case ChatUserJoinedEvent(eventNo, chatId, user, timestamp) =>
        ChatMessageProcessingResult(
          Some(Reply(createJoinResponse(), replyTo)),
          List(UserJoinedChat(chatId, eventNo, timestamp, user)))
    }
  }

  protected def onLeaveChannel(message: LeaveChatRequest): Try[ChatMessageProcessingResult[_]] = {
    val LeaveChatRequest(_, _, requester, _, replyTo) = message
    stateManager.onLeaveChannel(requester.userId) map {
      case ChatUserLeftEvent(eventNo, chatId, user, timestamp) =>
        val response = Reply(ChatActor.RequestSuccess(), replyTo)
        ChatMessageProcessingResult(Some(response), List(UserLeftChat(chatId, eventNo, timestamp, user)))
    }
  }

  protected def onAddUserToChannel(message: AddUserToChatRequest): Try[ChatMessageProcessingResult[_]] = {
    val AddUserToChatRequest(_, chatId, requester, userToAdd, replyTo) = message
    stateManager.onAddUserToChannel(chatId, requester.userId, userToAdd) map {
      case ChatUserAddedEvent(eventNo, chatId, user, timestamp, addedUserId) =>
        val response = Reply(ChatActor.RequestSuccess(), replyTo)
        ChatMessageProcessingResult(Some(response), List(UserAddedToChannel(chatId, eventNo, timestamp, user, addedUserId)))
    }
  }

  protected def onRemoveUserFromChannel(message: RemoveUserFromChatRequest): Try[ChatMessageProcessingResult[_]] = {
    val RemoveUserFromChatRequest(_, chatId, requester, userToRemove, replyTo) = message
    stateManager.onRemoveUserFromChannel(chatId, requester.userId, userToRemove) map {
      case ChatUserRemovedEvent(eventNo, chatId, user, timestamp, removedUserId) =>
        val response = Reply(ChatActor.RequestSuccess(), replyTo)
        ChatMessageProcessingResult(Some(response), List(UserRemovedFromChannel(chatId, eventNo, timestamp, user, removedUserId)))
    }
  }

  protected def onSetChatChannelName(message: SetChatNameRequest): Try[ChatMessageProcessingResult[_]] = {
    val SetChatNameRequest(_, chatId, requester, name, replyTo) = message
    stateManager.onSetChatChannelName(chatId, requester, name) map {
      case ChatNameChangedEvent(eventNo, chatId, user, timestamp, name) =>
        val response = Reply(ChatActor.RequestSuccess(), replyTo)
        ChatMessageProcessingResult(Some(response), List(ChatNameChanged(chatId, eventNo, timestamp, user, name)))
    }
  }

  protected  def onSetChatChannelTopic(message: SetChatTopicRequest): Try[ChatMessageProcessingResult[_]] = {
    val SetChatTopicRequest(_, chatId, requester, topic, replyTo) = message
    stateManager.onSetChatChannelTopic(chatId, requester, topic) map {
      case ChatTopicChangedEvent(eventNo, chatId, user, timestamp, topic) =>
        val response = Reply(ChatActor.RequestSuccess(), replyTo)
        ChatMessageProcessingResult(Some(response), List(ChatTopicChanged(chatId, eventNo, timestamp, user, topic)))
    }
  }

  protected def onMarkEventsSeen(message: MarkChatsEventsSeenRequest): Try[ChatMessageProcessingResult[_]] = {
    val MarkChatsEventsSeenRequest(_, chatId, requester, eventNumber, replyTo) = message
    stateManager.onMarkEventsSeen(chatId, requester.userId, eventNumber) map { _ =>
      val response = Reply(ChatActor.RequestSuccess(), replyTo)
      ChatMessageProcessingResult(Some(response), List(EventsMarkedSeen(chatId, eventNumber, requester)))
    }
  }

  protected def onGetHistory(message: GetChatHistoryRequest): Try[ChatMessageProcessingResult[_]] = {
    val GetChatHistoryRequest(_, chatId, _, offset, limit, startEvent, forward, eventTypes, messageFilter, replyTo) = message
    stateManager.onGetHistory(chatId, offset, limit, startEvent, forward, eventTypes, messageFilter) map { events =>
      val response = Reply(GetChatHistorySuccess(events), replyTo)
      ChatMessageProcessingResult(Some(response), List())
    }
  }

  protected def onPublishMessage(message: PublishChatMessageRequest): Try[ChatMessageProcessingResult[_]] = {
    val PublishChatMessageRequest(_, chatId, requester, msg, replyTo) = message
    stateManager.onPublishMessage(chatId, requester.userId, msg) map {
      case ChatMessageEvent(eventNo, chatId, _, timestamp, msg) =>
        val response = Reply(PublishChatMessageSuccess(eventNo, timestamp), replyTo)
        ChatMessageProcessingResult(Some(response), List(RemoteChatMessage(chatId, eventNo, timestamp, requester, msg)))
    }
  }

  protected def onRemoveChannel(message: RemoveChatRequest): Try[ChatMessageProcessingResult[_]] = {
    val RemoveChatRequest(_, chatId, requester, replyTo) = message
    stateManager.onRemoveChannel(chatId, requester) map { _ =>
      val response = Reply(ChatActor.RequestSuccess(), replyTo)
      ChatMessageProcessingResult(Some(response), List(ChannelRemoved(chatId)))
    }
  }

  protected def onAddPermissionsMessage(message: AddChatPermissionsRequest): Try[ChatMessageProcessingResult[_]] = {
    val AddChatPermissionsRequest(_, chatId, requester, world, user, group, replyTo) = message
    stateManager.onAddPermissions(chatId, requester.userId, world, user, group) map { _ =>
      val response = Reply(ChatActor.RequestSuccess(), replyTo)
      ChatMessageProcessingResult(Some(response), List())
    }
  }

  protected def onRemovePermissionsMessage(message: RemoveChatPermissionsRequest): Try[ChatMessageProcessingResult[_]] = {
    val RemoveChatPermissionsRequest(_, chatId, requester, world, user, group, replyTo) = message
    stateManager.onRemovePermissions(chatId, requester.userId, world, user, group) map { _ =>
      val response = Reply(ChatActor.RequestSuccess(), replyTo)
      ChatMessageProcessingResult(Some(response), List())
    }
  }

  protected def onSetPermissionsMessage(message: SetChatPermissionsRequest): Try[ChatMessageProcessingResult[_]] = {
    val SetChatPermissionsRequest(_, chatId, requester, world, user, group, replyTo) = message
    stateManager.onSetPermissions(chatId, requester.userId, world, user, group) map { _ =>
      ChatMessageProcessingResult(Some(Reply(ChatActor.RequestSuccess(), replyTo)), List())
    }
  }

  protected def onGetClientPermissions(message: GetClientChatPermissionsRequest): Try[ChatMessageProcessingResult[_]] = {
    val GetClientChatPermissionsRequest(_, chatId, requester, replyTo) = message
    stateManager.onGetClientPermissions(chatId, requester.userId) map { permissions =>
      val response = Reply(GetClientChatPermissionsSuccess(permissions), replyTo)
      ChatMessageProcessingResult(Some(response), List())
    }
  }

  protected def onGetWorldPermissions(message: GetWorldChatPermissionsRequest): Try[ChatMessageProcessingResult[_]] = {
    val GetWorldChatPermissionsRequest(_, chatId, _, replyTo) = message
    stateManager.onGetWorldPermissions(chatId) map { permissions =>
      val response = Reply(GetWorldChatPermissionsSuccess(permissions), replyTo)
      ChatMessageProcessingResult(Some(response), List())
    }
  }

  protected def onGetAllUserPermissions(message: GetAllUserChatPermissionsRequest): Try[ChatMessageProcessingResult[_]] = {
    val GetAllUserChatPermissionsRequest(_, chatId, _, replyTo) = message
    stateManager.onGetAllUserPermissions(chatId) map { permissions =>
      val map = permissions.groupBy { _.user } map { case (user, userPermissions) => DomainUserId(user.userType, user.username) -> userPermissions.map { _.permission } }
      val response = Reply(GetAllUserChatPermissionsSuccess(map), replyTo)
      ChatMessageProcessingResult(Some(response), List())
    }
  }

  protected def onGetAllGroupPermissions(message: GetAllGroupChatPermissionsRequest): Try[ChatMessageProcessingResult[_]] = {
    val GetAllGroupChatPermissionsRequest(_, chatId, _, replyTo) = message
    stateManager.onGetAllGroupPermissions(chatId) map { permissions =>
      val map = permissions.groupBy { _.group } map { case (group, groupPermissions) => group.id -> groupPermissions.map { _.permission } }
      val response = Reply(GetAllGroupChatPermissionsSuccess(map), replyTo)
      ChatMessageProcessingResult(Some(response), List())
    }
  }

  protected def onGetUserPermissions(message: GetUserChatPermissionsRequest): Try[ChatMessageProcessingResult[_]] = {
    val GetUserChatPermissionsRequest(_, chatId, requester, _, replyTo) = message
    stateManager.onGetUserPermissions(chatId, requester.userId) map { permissions =>
      val response = Reply(GetUserChatPermissionsSuccess(permissions), replyTo)
      ChatMessageProcessingResult(Some(response), List())
    }
  }

  protected def onGetGroupPermissions(message: GetGroupChatPermissionsRequest): Try[ChatMessageProcessingResult[_]] = {
    val GetGroupChatPermissionsRequest(_, chatId, _, groupId, replyTo) = message
    stateManager.onGetGroupPermissions(chatId, groupId) map { permissions =>
      val response = Reply(GetGroupChatPermissionsSuccess(permissions), replyTo)
      ChatMessageProcessingResult(Some(response), List())
    }
  }

  protected def createJoinResponse(): JoinChatResponse = {
    val ChatState(id, chatType, created, isPrivate, name, topic, lastEventTime, lastEventNo, members) = stateManager.state()
    val info = ChatInfo(id, chatType, created, isPrivate, name, topic, lastEventNo, lastEventTime, members.values.toSet)
    JoinChatSuccess(info)
  }
}

private[chat] case class ChatMessageProcessingResult[T](response: Option[Reply[T]], broadcastMessages: List[ChatClientActor.OutgoingMessage])
private[chat] case class Reply[T](response: T, replyTo: ActorRef[T])
