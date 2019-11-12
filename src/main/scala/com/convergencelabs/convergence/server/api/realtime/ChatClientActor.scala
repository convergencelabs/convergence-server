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

package com.convergencelabs.convergence.server.api.realtime

import akka.actor.{Actor, ActorLogging, ActorRef, Props, actorRef2Scala}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.chat._
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.server.api.realtime.ImplicitMessageConversions._
import com.convergencelabs.convergence.server.datastore.domain.{ChatMembership, ChatType}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserSessionId}
import com.convergencelabs.convergence.server.domain.chat.ChatLookupActor._
import com.convergencelabs.convergence.server.domain.chat.ChatMessages._
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatSharding}
import com.google.protobuf.timestamp.Timestamp
import scalapb.GeneratedMessage

import scala.language.postfixOps
import scala.util.{Failure, Success}

object ChatClientActor {
  def props(
    domainFqn: DomainId,
    chatLookupActor: ActorRef,
    session: DomainUserSessionId,
    requestTimeout: Timeout): Props =
    Props(new ChatClientActor(domainFqn, chatLookupActor, session, requestTimeout))
}

class ChatClientActor(
  domainFqn: DomainId,
  chatLookupActor: ActorRef,
  session: DomainUserSessionId,
  implicit val requestTimeout: Timeout) extends Actor with ActorLogging {

  implicit val ec = context.dispatcher
  
  val chatChannelActor = ChatSharding.shardRegion(context.system)

  val mediator = DistributedPubSub(context.system).mediator
  val chatTopicName = ChatActor.getChatUsernameTopicName(session.userId)

  mediator ! Subscribe(chatTopicName, self)

  def receive: Receive = {
    case SubscribeAck(Subscribe(chatTopicName, _, _)) ⇒
      log.debug("Subscribe to direct chat for user")

    case MessageReceived(message: NormalMessage with ChatMessage) =>
      onMessageReceived(message)
    case RequestReceived(message: RequestMessage with ChatMessage, replyPromise) =>
      onRequestReceived(message, replyPromise)
    case RequestReceived(message: RequestMessage with PermissionsMessage, replyPromise) =>
      onPermissionsRequestReceived(message, replyPromise)

    case message: ChatBroadcastMessage =>
      handleBroadcastMessage(message)

    case x: Any =>
      unhandled(x)
  }

  private[this] def handleBroadcastMessage(message: ChatBroadcastMessage): Unit = {
    message match {
      // Broadcast messages
      case RemoteChatMessage(chatId, eventNumber, timestamp, session, message) =>
        if (this.session != session) {
          // We don't need to send this back to ourselves
          context.parent ! RemoteChatMessageMessage(chatId, eventNumber,
            Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)),
            session.sessionId,
            message)
        }

      case UserJoinedChat(chatId, eventNumber, timestamp, userId) =>
        context.parent ! UserJoinedChatMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId))

      case UserLeftChat(chatId, eventNumber, timestamp, userId) =>
        context.parent ! UserLeftChatMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId))

      case UserAddedToChannel(chatId, eventNumber, timestamp, userId, addedUser) =>
        context.parent ! UserAddedToChatChannelMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId), Some(addedUser))

      case UserRemovedFromChannel(chatId, eventNumber, timestamp, userId, removedUser) =>
        context.parent ! UserRemovedFromChatChannelMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId), Some(removedUser))

      case ChannelRemoved(chatId) =>
        context.parent ! ChatRemovedMessage(chatId)

      case ChatNameChanged(chatId, eventNumber, timestamp, userId, name) =>
        context.parent ! ChatNameSetMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId), name)

      case ChatTopicChanged(chatId, eventNumber, timestamp, userId, topic) =>
        context.parent ! ChatTopicSetMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId), topic)
    }
  }

  //
  // Incoming Messages
  //

  def onMessageReceived(message: NormalMessage with ChatMessage): Unit = {
    log.error("Chat client actor received a non-request message")
  }

  def onRequestReceived(message: RequestMessage with ChatMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case message: CreateChatRequestMessage =>
        onCreateChannel(message, replyCallback)
      case message: RemoveChatRequestMessage =>
        onRemoveChannel(message, replyCallback)
      case message: JoinChatRequestMessage =>
        onJoinChannel(message, replyCallback)
      case message: LeaveChatRequestMessage =>
        onLeaveChannel(message, replyCallback)
      case message: AddUserToChatChannelRequestMessage =>
        onAddUserToChannel(message, replyCallback)
      case message: RemoveUserFromChatChannelRequestMessage =>
        onRemoveUserFromChannel(message, replyCallback)
      case message: SetChatNameRequestMessage =>
        onSetChatChannelName(message, replyCallback)
      case message: SetChatTopicRequestMessage =>
        onSetChatChannelTopic(message, replyCallback)
      case message: MarkChatEventsSeenRequestMessage =>
        onMarkEventsSeen(message, replyCallback)
      case message: GetChatsRequestMessage =>
        onGetChannels(message, replyCallback)
      case message: GetJoinedChatsRequestMessage =>
        onGetJoinedChannels(replyCallback)
      case message: GetDirectChatsRequestMessage =>
        onGetDirect(message, replyCallback)
      case message: ChatHistoryRequestMessage =>
        onGetHistory(message, replyCallback)
      case message: PublishChatRequestMessage =>
        onPublishMessage(message, replyCallback)
      case message: ChatsExistRequestMessage =>
        onChannelsExist(message, replyCallback)
    }
  }

  def onPermissionsRequestReceived(message: RequestMessage with PermissionsMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case message: AddPermissionsRequestMessage =>
        onAddChatPermissions(message, replyCallback)
      case message: RemovePermissionsRequestMessage =>
        onRemoveChatPermissions(message, replyCallback)
      case message: SetPermissionsRequestMessage =>
        onSetChatPermissions(message, replyCallback)
      case message: GetClientPermissionsRequestMessage =>
        onGetClientChatPermissions(message, replyCallback)
      case message: GetWorldPermissionsRequestMessage =>
        onGetWorldPermissions(message, replyCallback)
      case message: GetAllUserPermissionsRequestMessage =>
        onGetAllUserPermissions(message, replyCallback)
      case message: GetUserPermissionsRequestMessage =>
        onGetUserPermissions(message, replyCallback)
      case message: GetAllGroupPermissionsRequestMessage =>
        onGetAllGroupPermissions(message, replyCallback)
      case message: GetGroupPermissionsRequestMessage =>
        onGetGroupPermissions(message, replyCallback)
    }
  }

  def onCreateChannel(message: CreateChatRequestMessage, cb: ReplyCallback): Unit = {
    val CreateChatRequestMessage(chatId, chatType, membership, name, topic, memberData) = message;
    val members = memberData.toSet.map(ImplicitMessageConversions.dataToDomainUserId(_))
    val request = CreateChatRequest(chatId, session.userId, ChatType.parse(chatType), ChatMembership.parse(membership), Some(name), Some(topic), members)
    chatLookupActor.ask(request).mapTo[CreateChatResponse] onComplete {
      case Success(CreateChatResponse(chatId)) =>
        cb.reply(CreateChatResponseMessage(chatId))
      case Failure(cause: ChatException) =>
        this.handleChatChannelException(cause, cb)
      case Failure(cause) =>
        log.error(cause, "could not create chat: " + message)
        cb.unexpectedError("An unexcpeected error occurred creating the chat")
    }
  }

  def onRemoveChannel(message: RemoveChatRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveChatRequestMessage(chatId) = message;
    val request = RemoveChatlRequest(domainFqn, chatId, session.userId)
    handleSimpleChannelRequest(request, { () => RemoveChatResponseMessage() }, cb)
  }

  def onJoinChannel(message: JoinChatRequestMessage, cb: ReplyCallback): Unit = {
    val JoinChatRequestMessage(chatId) = message;
    val request = JoinChannelRequest(domainFqn, chatId, session, self)
    chatChannelActor.ask(request).mapTo[JoinChannelResponse] onComplete {
      case Success(JoinChannelResponse(info)) =>
        cb.reply(JoinChatResponseMessage(Some(channelInfoToMessage(info))))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onLeaveChannel(message: LeaveChatRequestMessage, cb: ReplyCallback): Unit = {
    val LeaveChatRequestMessage(chatId) = message;
    val request = LeaveChannelRequest(domainFqn, chatId, session, self)
    handleSimpleChannelRequest(request, { () => LeaveChatResponseMessage() }, cb)
  }

  def onAddUserToChannel(message: AddUserToChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val AddUserToChatChannelRequestMessage(chatId, userToAdd) = message;
    val request = AddUserToChannelRequest(domainFqn, chatId, session, ImplicitMessageConversions.dataToDomainUserId(userToAdd.get))
    handleSimpleChannelRequest(request, { () => AddUserToChatChannelResponseMessage() }, cb)
  }

  def onRemoveUserFromChannel(message: RemoveUserFromChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveUserFromChatChannelRequestMessage(chatId, userToRemove) = message;
    val request = RemoveUserFromChannelRequest(domainFqn, chatId, session, ImplicitMessageConversions.dataToDomainUserId(userToRemove.get))
    handleSimpleChannelRequest(request, { () => RemoveUserFromChatChannelResponseMessage() }, cb)
  }

  def onSetChatChannelName(message: SetChatNameRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatNameRequestMessage(chatId, name) = message;
    val request = SetChatNameRequest(domainFqn, chatId, session.userId, name)
    handleSimpleChannelRequest(request, { () => SetChatNameResponseMessage() }, cb)
  }

  def onSetChatChannelTopic(message: SetChatTopicRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatTopicRequestMessage(chatId, topic) = message;
    val request = SetChatTopicRequest(domainFqn, chatId, session.userId, topic)
    handleSimpleChannelRequest(request, { () => SetChatTopicResponseMessage() }, cb)
  }

  def onMarkEventsSeen(message: MarkChatEventsSeenRequestMessage, cb: ReplyCallback): Unit = {
    val MarkChatEventsSeenRequestMessage(chatId, eventNumber) = message;
    val request = MarkChannelEventsSeenRequest(domainFqn, chatId, session, eventNumber)
    handleSimpleChannelRequest(request, { () => MarkChatEventsSeenResponseMessage() }, cb)
  }

  def onAddChatPermissions(message: AddPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val AddPermissionsRequestMessage(idType, id, worldPermissionData, userPermissionData, groupPermissionData) = message;
    val groupPermissions = groupPermissionData map {
      case (groupId, permissions) => GroupPermissions(groupId, permissions.values.toSet)
    }
    val userPermissions = userPermissionData
      .map(p => UserPermissions(ImplicitMessageConversions.dataToDomainUserId(p.user.get), p.permissions.toSet))

    val request = AddChatPermissionsRequest(domainFqn, id, session, Some(worldPermissionData.toSet), Some(userPermissions.toSet), Some(groupPermissions.toSet))
    handleSimpleChannelRequest(request, { () => AddPermissionsResponseMessage() }, cb)
  }

  def onRemoveChatPermissions(message: RemovePermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val RemovePermissionsRequestMessage(idType, id, worldPermissionData, userPermissionData, groupPermissionData) = message;
    val groupPermissions = groupPermissionData map {
      case (groupId, permissions) => GroupPermissions(groupId, permissions.values.toSet)
    }
    val userPermissions = userPermissionData
      .map(p => UserPermissions(ImplicitMessageConversions.dataToDomainUserId(p.user.get), p.permissions.toSet))
    val request = RemoveChatPermissionsRequest(domainFqn, id, session, Some(worldPermissionData.toSet), Some(userPermissions.toSet), Some(groupPermissions.toSet))
    handleSimpleChannelRequest(request, { () => RemovePermissionsResponseMessage() }, cb)
  }

  def onSetChatPermissions(message: SetPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val SetPermissionsRequestMessage(idType, id, worldPermissionData, userPermissionData, groupPermissionData) = message;
    val groupPermissions = groupPermissionData map {
      case (groupId, permissions) => GroupPermissions(groupId, permissions.values.toSet)
    }
    val userPermissions = userPermissionData
      .map(p => UserPermissions(ImplicitMessageConversions.dataToDomainUserId(p.user.get), p.permissions.toSet))
    val request = SetChatPermissionsRequest(domainFqn, id, session, Some(worldPermissionData.toSet), Some(userPermissions.toSet), Some(groupPermissions.toSet))
    handleSimpleChannelRequest(request, { () => SetPermissionsResponseMessage() }, cb)
  }

  def onGetClientChatPermissions(message: GetClientPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetClientPermissionsRequestMessage(idType, id) = message;
    val request = GetClientChatPermissionsRequest(domainFqn, id, session)
    chatChannelActor.ask(request).mapTo[GetClientChatPermissionsResponse] onComplete {
      case Success(GetClientChatPermissionsResponse(permissions)) =>
        cb.reply(GetClientPermissionsResponseMessage(permissions.toSeq))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetWorldPermissions(message: GetWorldPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetWorldPermissionsRequestMessage(idType, id) = message;
    val request = GetWorldChatPermissionsRequest(domainFqn, id, session)
    chatChannelActor.ask(request).mapTo[GetWorldChatPermissionsResponse] onComplete {
      case Success(GetWorldChatPermissionsResponse(permissions)) =>
        cb.reply(GetWorldPermissionsResponseMessage(permissions.toSeq))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetAllUserPermissions(message: GetAllUserPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetAllUserPermissionsRequestMessage(idType, id) = message;
    val request = GetAllUserChatPermissionsRequest(domainFqn, id, session)
    chatChannelActor.ask(request).mapTo[GetAllUserChatPermissionsResponse] onComplete {
      case Success(GetAllUserChatPermissionsResponse(users)) =>
        val userPermissionEntries = users.map { case (userId, permissions) => 
            (userId, UserPermissionsEntry(Some(ImplicitMessageConversions.domainUserIdToData(userId)), permissions.toSeq)) 
         }
        cb.reply(GetAllUserPermissionsResponseMessage(userPermissionEntries.values.toSeq))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetAllGroupPermissions(message: GetAllGroupPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetAllGroupPermissionsRequestMessage(idType, id) = message;
    val request = GetAllGroupChatPermissionsRequest(domainFqn, id, session)
    chatChannelActor.ask(request).mapTo[GetAllGroupChatPermissionsResponse] onComplete {
      case Success(GetAllGroupChatPermissionsResponse(groups)) =>
        cb.reply(GetAllGroupPermissionsResponseMessage(groups map { case (key, value) => (key, PermissionsList(value.toSeq)) }))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetUserPermissions(message: GetUserPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetUserPermissionsRequestMessage(idType, id, user) = message;
    val request = GetUserChatPermissionsRequest(domainFqn, id, session, ImplicitMessageConversions.dataToDomainUserId(user.get))
    chatChannelActor.ask(request).mapTo[GetUserChatPermissionsResponse] onComplete {
      case Success(GetUserChatPermissionsResponse(permissions)) =>
        cb.reply(GetUserPermissionsResponseMessage(permissions.toSeq))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetGroupPermissions(message: GetGroupPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetGroupPermissionsRequestMessage(idType, id, groupId) = message;
    val request = GetGroupChatPermissionsRequest(domainFqn, id, session, groupId)
    chatChannelActor.ask(request).mapTo[GetGroupChatPermissionsResponse] onComplete {
      case Success(GetGroupChatPermissionsResponse(permissions)) =>
        cb.reply(GetGroupPermissionsResponseMessage(permissions.toSeq))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onPublishMessage(message: PublishChatRequestMessage, cb: ReplyCallback): Unit = {
    val PublishChatRequestMessage(chatId, msg) = message;
    val request = PublishChatMessageRequest(domainFqn, chatId, session, msg)
    handleSimpleChannelRequest(request, { () => PublishChatResponseMessage() }, cb)
  }

  def onChannelsExist(message: ChatsExistRequestMessage, cb: ReplyCallback): Unit = {
    val ChatsExistRequestMessage(chatIds) = message;
    val request = ChannelsExistsRequest(session.userId, chatIds.toList)
    chatLookupActor.ask(request).mapTo[ChannelsExistsResponse] onComplete {
      case Success(ChannelsExistsResponse(channels)) =>
        cb.reply(ChatsExistResponseMessage(channels))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetChannels(message: GetChatsRequestMessage, cb: ReplyCallback): Unit = {
    val GetChatsRequestMessage(ids) = message;
    val request = GetChannelsRequest(session.userId, ids.toList)
    chatLookupActor.ask(request).mapTo[GetChannelsResponse] onComplete {
      case Success(GetChannelsResponse(channels)) =>
        val info = channels.map(channelInfoToMessage(_))
        cb.reply(GetChatsResponseMessage(info))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetDirect(message: GetDirectChatsRequestMessage, cb: ReplyCallback): Unit = {
    val GetDirectChatsRequestMessage(usernameLists) = message;
    val request = GetDirectChannelsRequest(
        session.userId, 
        usernameLists.map(_.values.map(ImplicitMessageConversions.dataToDomainUserId(_)).toSet).toList)
    chatLookupActor.ask(request).mapTo[GetDirectChannelsResponse] onComplete {
      case Success(GetDirectChannelsResponse(channels)) =>
        val info = channels.map(channelInfoToMessage(_))
        cb.reply(GetChatsResponseMessage(info))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetJoinedChannels(cb: ReplyCallback): Unit = {
    val request = GetJoinedChannelsRequest(session.userId)
    chatLookupActor.ask(request).mapTo[GetJoinedChannelsResponse] onComplete {
      case Success(GetJoinedChannelsResponse(channels)) =>
        val info = channels.map(channelInfoToMessage(_))
        cb.reply(GetChatsResponseMessage(info))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetHistory(message: ChatHistoryRequestMessage, cb: ReplyCallback): Unit = {
    val ChatHistoryRequestMessage(chatId, limit, startEvent, forward, eventFilter) = message;
    val request = GetChannelHistoryRequest(domainFqn, chatId, session, limit, startEvent, forward, Some(eventFilter.toList))
    chatChannelActor.ask(request).mapTo[GetChannelHistoryResponse] onComplete {
      case Success(GetChannelHistoryResponse(events)) =>
        val eventData = events.map(channelEventToMessage(_))
        val reply = ChatHistoryResponseMessage(eventData.toSeq)
        cb.reply(reply)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
    ()
  }

  private[this] def handleSimpleChannelRequest(request: Any, response: () => GeneratedMessage with ResponseMessage, cb: ReplyCallback): Unit = {
    chatChannelActor.ask(request).mapTo[Unit] onComplete {
      case Success(()) =>
        val r = response()
        cb.reply(r)
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def handleUnexpectedError(request: Any, cause: Throwable, cb: ReplyCallback): Unit = {
    log.error(cause, "Unexpected error processing chat request " + request)
    cb.unexpectedError("Unexpected error processing chat request")
  }

  private[this] def handleChatChannelException(cause: ChatException, cb: ReplyCallback): Unit = {
    cause match {
      case ChatNotFoundException(chatId) =>
        cb.expectedError(
          "chat_not_found",
          s"Could not complete the request because a chat with id '${chatId}' does not exist.",
          Map("chatId" -> chatId))
      case ChatNotJoinedException(chatId) =>
        cb.expectedError(
          "chat_not_joined",
          s"Could not complete the request the user is not joined to the chat: '${chatId}'",
          Map("chatId" -> chatId))
      case ChatAlreadyExistsException(chatId) =>
        cb.expectedError(
          "chat_already_exists",
          s"Could not complete the request because a chat with id '${chatId}' aready exists.",
          Map("chatId" -> chatId))
      case ChatAlreadyJoinedException(chatId) =>
        cb.expectedError(
          "chat_already_joined",
          s"Could not complete the request the user is already joined to the chat: '${chatId}'",
          Map("chatId" -> chatId))
      case InvalidChatMessageExcpetion(message) =>
        cb.expectedError(
          "invalid_chat_message",
          s"The message that was sent was not valid for this type of chat: '${message}'",
          Map())
    }
  }

  private[this] def toChatEventCode: PartialFunction[Int, String] = {
    case 0 => "created"
    case 1 => "message"
    case 2 => "user_joined"
    case 3 => "user_left"
    case 4 => "user_added"
    case 5 => "user_removed"
    case 6 => "name_changed"
    case 7 => "topic_changed"
  }
}
