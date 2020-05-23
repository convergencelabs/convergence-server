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

import java.time.Instant

import akka.actor.{Actor, ActorLogging, ActorRef, Props, actorRef2Scala}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.chat._
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.api.realtime.ImplicitMessageConversions._
import com.convergencelabs.convergence.server.datastore.domain.ChatMembership.InvalidChatMembershipValue
import com.convergencelabs.convergence.server.datastore.domain.ChatType.InvalidChatTypeValue
import com.convergencelabs.convergence.server.datastore.domain.{ChatEvent, ChatInfo, ChatMembership, ChatType}
import com.convergencelabs.convergence.server.domain.chat.ChatManagerActor._
import com.convergencelabs.convergence.server.domain.chat.ChatMessages._
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatSharding}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId, DomainUserSessionId}
import com.google.protobuf.timestamp.Timestamp
import org.json4s.JsonAST.JString
import scalapb.GeneratedMessage

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class ChatClientActor(domainFqn: DomainId,
                      chatManagerActor: ActorRef,
                      session: DomainUserSessionId,
                      implicit val requestTimeout: Timeout) extends Actor with ActorLogging {

  import ChatClientActor._

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  private[this] val chatShardRegion = ChatSharding.shardRegion(context.system)

  private[this] val mediator = DistributedPubSub(context.system).mediator
  private[this] val chatTopicName = ChatActor.getChatUsernameTopicName(session.userId)

  mediator ! Subscribe(chatTopicName, self)

  def receive: Receive = {
    case SubscribeAck(Subscribe(_, _, _)) =>
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
        context.parent ! RemoteChatMessageMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)),
          session.sessionId,
          message)

      case EventsMarkedSeen(chatId: String, eventNumber: Long, session: DomainUserSessionId) =>
        context.parent ! ChatEventsMarkedSeenMessage(chatId, Some(domainUserIdToData(session.userId)), eventNumber)

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
      case _: GetJoinedChatsRequestMessage =>
        onGetJoinedChannels(replyCallback)
      case message: GetDirectChatsRequestMessage =>
        onGetDirect(message, replyCallback)
      case message: ChatHistoryRequestMessage =>
        onGetHistory(message, replyCallback)
      case message: PublishChatRequestMessage =>
        onPublishMessage(message, replyCallback)
      case message: ChatsExistRequestMessage =>
        onChannelsExist(message, replyCallback)
      case message: ChatsSearchRequestMessage =>
        onChatsSearch(message, replyCallback)
    }
  }

  private[this] def onPermissionsRequestReceived(message: RequestMessage with PermissionsMessage, replyCallback: ReplyCallback): Unit = {
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

  private[this] def onCreateChannel(message: CreateChatRequestMessage, cb: ReplyCallback): Unit = {
    val CreateChatRequestMessage(chatId, chatType, membership, name, topic, memberData, _) = message
    val members = memberData.toSet.map(ImplicitMessageConversions.dataToDomainUserId)
    (for {
      t <- ChatType.parse(chatType)
      m <- ChatMembership.parse(membership)
    } yield {
      val request = CreateChatRequest(chatId, session.userId, t, m, Some(name), Some(topic), members)
      chatManagerActor.ask(request).mapTo[CreateChatResponse] onComplete {
        case Success(CreateChatResponse(chatId)) =>
          cb.reply(CreateChatResponseMessage(chatId))
        case Failure(cause: ChatException) =>
          this.handleChatChannelException(cause, cb)
        case Failure(cause) =>
          log.error(cause, "could not create chat: " + message)
          cb.unexpectedError("An unexpected error occurred creating the chat")
      }
    }) recover {
      case InvalidChatTypeValue(value) =>
        cb.unexpectedError("Invalid chat type: " + value)
      case InvalidChatMembershipValue(value) =>
        cb.unexpectedError("Invalid chat membership: " + value)
      case cause =>
        log.error("Unexpected error creating chat", cause)
        cb.unknownError()
    }
  }

  private[this] def onRemoveChannel(message: RemoveChatRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveChatRequestMessage(chatId, _) = message
    val request = RemoveChatRequest(domainFqn, chatId, session.userId)
    handleSimpleChannelRequest(request, { () => RemoveChatResponseMessage() }, cb)
  }

  private[this] def onJoinChannel(message: JoinChatRequestMessage, cb: ReplyCallback): Unit = {
    val JoinChatRequestMessage(chatId, _) = message
    val request = JoinChannelRequest(domainFqn, chatId, session, self)
    chatShardRegion.ask(request).mapTo[JoinChannelResponse] onComplete {
      case Success(JoinChannelResponse(info)) =>
        cb.reply(JoinChatResponseMessage(Some(channelInfoToMessage(info))))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def onLeaveChannel(message: LeaveChatRequestMessage, cb: ReplyCallback): Unit = {
    val LeaveChatRequestMessage(chatId, _) = message
    val request = LeaveChannelRequest(domainFqn, chatId, session, self)
    handleSimpleChannelRequest(request, { () => LeaveChatResponseMessage() }, cb)
  }

  private[this] def onAddUserToChannel(message: AddUserToChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val AddUserToChatChannelRequestMessage(chatId, userToAdd, _) = message
    val request = AddUserToChannelRequest(domainFqn, chatId, session, ImplicitMessageConversions.dataToDomainUserId(userToAdd.get))
    handleSimpleChannelRequest(request, { () => AddUserToChatChannelResponseMessage() }, cb)
  }

  private[this] def onRemoveUserFromChannel(message: RemoveUserFromChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveUserFromChatChannelRequestMessage(chatId, userToRemove, _) = message
    val request = RemoveUserFromChannelRequest(domainFqn, chatId, session, ImplicitMessageConversions.dataToDomainUserId(userToRemove.get))
    handleSimpleChannelRequest(request, { () => RemoveUserFromChatChannelResponseMessage() }, cb)
  }

  private[this] def onSetChatChannelName(message: SetChatNameRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatNameRequestMessage(chatId, name, _) = message
    val request = SetChatNameRequest(domainFqn, chatId, session.userId, name)
    handleSimpleChannelRequest(request, { () => SetChatNameResponseMessage() }, cb)
  }

  private[this] def onSetChatChannelTopic(message: SetChatTopicRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatTopicRequestMessage(chatId, topic, _) = message
    val request = SetChatTopicRequest(domainFqn, chatId, session.userId, topic)
    handleSimpleChannelRequest(request, { () => SetChatTopicResponseMessage() }, cb)
  }

  private[this] def onMarkEventsSeen(message: MarkChatEventsSeenRequestMessage, cb: ReplyCallback): Unit = {
    val MarkChatEventsSeenRequestMessage(chatId, eventNumber, _) = message
    val request = MarkChannelEventsSeenRequest(domainFqn, chatId, session, eventNumber)
    handleSimpleChannelRequest(request, { () => MarkChatEventsSeenResponseMessage() }, cb)
  }

  private[this] def onAddChatPermissions(message: AddPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val AddPermissionsRequestMessage(_, id, worldPermissionData, userPermissionData, groupPermissionData, _) = message
    val groupPermissions = groupPermissionData map {
      case (groupId, permissions) => GroupPermissions(groupId, permissions.values.toSet)
    }
    val userPermissions = userPermissionData
      .map(p => UserPermissions(ImplicitMessageConversions.dataToDomainUserId(p.user.get), p.permissions.toSet))

    val request = AddChatPermissionsRequest(domainFqn, id, session, Some(worldPermissionData.toSet), Some(userPermissions.toSet), Some(groupPermissions.toSet))
    handleSimpleChannelRequest(request, { () => AddPermissionsResponseMessage() }, cb)
  }

  private[this] def onRemoveChatPermissions(message: RemovePermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val RemovePermissionsRequestMessage(_, id, worldPermissionData, userPermissionData, groupPermissionData, _) = message
    val groupPermissions = groupPermissionData map {
      case (groupId, permissions) => GroupPermissions(groupId, permissions.values.toSet)
    }
    val userPermissions = userPermissionData
      .map(p => UserPermissions(ImplicitMessageConversions.dataToDomainUserId(p.user.get), p.permissions.toSet))
    val request = RemoveChatPermissionsRequest(domainFqn, id, session, Some(worldPermissionData.toSet), Some(userPermissions.toSet), Some(groupPermissions.toSet))
    handleSimpleChannelRequest(request, { () => RemovePermissionsResponseMessage() }, cb)
  }

  private[this] def onSetChatPermissions(message: SetPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val SetPermissionsRequestMessage(_, id, worldPermissionData, userPermissionData, groupPermissionData, _) = message
    val groupPermissions = groupPermissionData map {
      case (groupId, permissions) => GroupPermissions(groupId, permissions.values.toSet)
    }
    val userPermissions = userPermissionData
      .map(p => UserPermissions(ImplicitMessageConversions.dataToDomainUserId(p.user.get), p.permissions.toSet))
    val request = SetChatPermissionsRequest(domainFqn, id, session, Some(worldPermissionData.toSet), Some(userPermissions.toSet), Some(groupPermissions.toSet))
    handleSimpleChannelRequest(request, { () => SetPermissionsResponseMessage() }, cb)
  }

  private[this] def onGetClientChatPermissions(message: GetClientPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetClientPermissionsRequestMessage(_, id, _) = message
    val request = GetClientChatPermissionsRequest(domainFqn, id, session)
    chatShardRegion.ask(request).mapTo[GetClientChatPermissionsResponse] onComplete {
      case Success(GetClientChatPermissionsResponse(permissions)) =>
        cb.reply(GetClientPermissionsResponseMessage(permissions.toSeq))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def onGetWorldPermissions(message: GetWorldPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetWorldPermissionsRequestMessage(_, id, _) = message
    val request = GetWorldChatPermissionsRequest(domainFqn, id, session)
    chatShardRegion.ask(request).mapTo[GetWorldChatPermissionsResponse] onComplete {
      case Success(GetWorldChatPermissionsResponse(permissions)) =>
        cb.reply(GetWorldPermissionsResponseMessage(permissions.toSeq))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def onGetAllUserPermissions(message: GetAllUserPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetAllUserPermissionsRequestMessage(_, id, _) = message
    val request = GetAllUserChatPermissionsRequest(domainFqn, id, session)
    chatShardRegion.ask(request).mapTo[GetAllUserChatPermissionsResponse] onComplete {
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

  private[this] def onGetAllGroupPermissions(message: GetAllGroupPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetAllGroupPermissionsRequestMessage(_, id, _) = message
    val request = GetAllGroupChatPermissionsRequest(domainFqn, id, session)
    chatShardRegion.ask(request).mapTo[GetAllGroupChatPermissionsResponse] onComplete {
      case Success(GetAllGroupChatPermissionsResponse(groups)) =>
        cb.reply(GetAllGroupPermissionsResponseMessage(groups map { case (key, value) => (key, PermissionsList(value.toSeq)) }))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def onGetUserPermissions(message: GetUserPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetUserPermissionsRequestMessage(_, id, user, _) = message
    val request = GetUserChatPermissionsRequest(domainFqn, id, session, ImplicitMessageConversions.dataToDomainUserId(user.get))
    chatShardRegion.ask(request).mapTo[GetUserChatPermissionsResponse] onComplete {
      case Success(GetUserChatPermissionsResponse(permissions)) =>
        cb.reply(GetUserPermissionsResponseMessage(permissions.toSeq))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def onGetGroupPermissions(message: GetGroupPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetGroupPermissionsRequestMessage(_, id, groupId, _) = message
    val request = GetGroupChatPermissionsRequest(domainFqn, id, session, groupId)
    chatShardRegion.ask(request).mapTo[GetGroupChatPermissionsResponse] onComplete {
      case Success(GetGroupChatPermissionsResponse(permissions)) =>
        cb.reply(GetGroupPermissionsResponseMessage(permissions.toSeq))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def onPublishMessage(message: PublishChatRequestMessage, cb: ReplyCallback): Unit = {
    val PublishChatRequestMessage(chatId, msg, _) = message
    val request = PublishChatMessageRequest(domainFqn, chatId, session, msg)

    chatShardRegion.ask(request).mapTo[PublishChatMessageResponse] onComplete {
      case Success(PublishChatMessageResponse(eventNumber, timestamp)) =>
        cb.reply(PublishChatResponseMessage(eventNumber, Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano))))
      case Failure(cause: ChatException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def onChannelsExist(message: ChatsExistRequestMessage, cb: ReplyCallback): Unit = {
    val ChatsExistRequestMessage(chatIds, _) = message
    val request = ChatsExistsRequest(session.userId, chatIds.toList)
    chatManagerActor.ask(request).mapTo[ChatsExistsResponse] onComplete {
      case Success(ChatsExistsResponse(channels)) =>
        cb.reply(ChatsExistResponseMessage(channels))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def onGetChannels(message: GetChatsRequestMessage, cb: ReplyCallback): Unit = {
    val GetChatsRequestMessage(ids, _) = message
    val request = GetChatsRequest(session.userId, ids.toSet)
    chatManagerActor.ask(request).mapTo[GetChatsResponse] onComplete {
      case Success(GetChatsResponse(channels)) =>
        val info = channels.map(channelInfoToMessage)
        cb.reply(GetChatsResponseMessage(info.toList))

      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def onGetDirect(message: GetDirectChatsRequestMessage, cb: ReplyCallback): Unit = {
    val GetDirectChatsRequestMessage(usernameLists, _) = message
    val request = GetDirectChatsRequest(
      session.userId,
      usernameLists.map(_.values.map(ImplicitMessageConversions.dataToDomainUserId).toSet).toSet)
    chatManagerActor.ask(request).mapTo[GetDirectChatsResponse] onComplete {
      case Success(GetDirectChatsResponse(channels)) =>
        val info = channels.map(channelInfoToMessage)
        cb.reply(GetDirectChatsResponseMessage(info.toList))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def onGetJoinedChannels(cb: ReplyCallback): Unit = {
    val request = GetJoinedChatsRequest(session.userId)
    chatManagerActor.ask(request).mapTo[GetJoinedChatsResponse] onComplete {
      case Success(GetJoinedChatsResponse(channels)) =>
        val info = channels.map(channelInfoToMessage)
        cb.reply(GetJoinedChatsResponseMessage(info.toList))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def onGetHistory(message: ChatHistoryRequestMessage, cb: ReplyCallback): Unit = {
    val ChatHistoryRequestMessage(chatId, offset, limit, startEvent, forward, eventFilter, _) = message
    val request = GetChatHistoryRequest(domainFqn, chatId, Some(session), offset, limit, startEvent, forward, Some(eventFilter.toSet), None)
    chatShardRegion.ask(request).mapTo[PagedData[ChatEvent]] onComplete {
      case Success(PagedData(events, startIndex, totalResults)) =>
        val eventData = events.map(channelEventToMessage)
        val reply = ChatHistoryResponseMessage(eventData, totalResults, totalResults)
        cb.reply(reply)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
    ()
  }

  private[this] def onChatsSearch(message: ChatsSearchRequestMessage, cb: ReplyCallback): Unit = {
    val ChatsSearchRequestMessage(term, fields, chatTypes, membershipString, offset, limit, _) = message
    (for {
      membership <- if (membershipString == "") {
        Success(None)
      } else {
        ChatMembership.parse(membershipString).map(Some(_))
      }
      types <- Try {
        chatTypes.map(t => ChatType.parse(t).get)
      }
    } yield {
      val searchTerm = if (term == "") None else Some(term)
      val searchFields = if (fields.isEmpty) None else Some(fields.toSet)
      val chatTypes = if (types.isEmpty) None else Some(types.toSet)

      val request = ChatsSearchRequest(searchTerm, searchFields, chatTypes, membership, offset, limit)
      chatManagerActor.ask(request).mapTo[PagedData[ChatInfo]] onComplete {
        case Success(PagedData(chats, startIndex, totalResults)) =>
          val chatInfoData = chats.map(channelInfoToMessage)
          val reply = ChatsSearchResponseMessage(chatInfoData, startIndex, totalResults)
          cb.reply(reply)
        case Failure(cause) =>
          handleUnexpectedError(request, cause, cb)
      }
      ()
    }) recover {
      case InvalidChatTypeValue(value) =>
        cb.unexpectedError("Invalid chat type: " + value)
      case InvalidChatMembershipValue(value) =>
        cb.unexpectedError("Invalid chat membership: " + value)
      case cause =>
        log.error("Unexpected error searching chats", cause)
        cb.unknownError()
    }
  }

  private[this] def handleSimpleChannelRequest(request: Any, response: () => GeneratedMessage with ResponseMessage, cb: ReplyCallback): Unit = {
    chatShardRegion.ask(request).mapTo[Unit] onComplete {
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
          s"Could not complete the request because a chat with id '$chatId' does not exist.",
          Map("chatId" -> JString(chatId)))
      case ChatNotJoinedException(chatId) =>
        cb.expectedError(
          "chat_not_joined",
          s"Could not complete the request the user is not joined to the chat: '$chatId'",
          Map("chatId" -> JString(chatId)))
      case ChatAlreadyExistsException(chatId) =>
        cb.expectedError(
          "chat_already_exists",
          s"Could not complete the request because a chat with id '$chatId' already exists.",
          Map("chatId" -> JString(chatId)))
      case ChatAlreadyJoinedException(chatId) =>
        cb.expectedError(
          "chat_already_joined",
          s"Could not complete the request the user is already joined to the chat: '$chatId'",
          Map("chatId" -> JString(chatId)))
      case InvalidChatMessageException(message) =>
        cb.expectedError(
          "invalid_chat_message",
          s"The message that was sent was not valid for this type of chat: '$message'",
          Map())
    }
  }
}

object ChatClientActor {
  def props(domainFqn: DomainId,
            chatLookupActor: ActorRef,
            session: DomainUserSessionId,
            requestTimeout: Timeout): Props =
    Props(new ChatClientActor(domainFqn, chatLookupActor, session, requestTimeout))


  sealed trait ChatBroadcastMessage extends CborSerializable{
    val chatId: String
  }

  case class UserJoinedChat(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId) extends ChatBroadcastMessage

  case class UserLeftChat(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId) extends ChatBroadcastMessage

  case class UserAddedToChannel(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, addedUserId: DomainUserId) extends ChatBroadcastMessage

  case class UserRemovedFromChannel(chatId: String, eventNumber: Int, timestamp: Instant, userId: DomainUserId, removedUserId: DomainUserId) extends ChatBroadcastMessage

  case class ChatNameChanged(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, name: String) extends ChatBroadcastMessage

  case class ChatTopicChanged(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, topic: String) extends ChatBroadcastMessage

  case class ChannelRemoved(chatId: String) extends ChatBroadcastMessage

  case class RemoteChatMessage(chatId: String, eventNumber: Long, timestamp: Instant, session: DomainUserSessionId, message: String) extends ChatBroadcastMessage

  case class EventsMarkedSeen(chatId: String, eventNumber: Long, session: DomainUserSessionId) extends ChatBroadcastMessage
}
