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

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.chat._
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.api.realtime.ImplicitMessageConversions._
import com.convergencelabs.convergence.server.datastore.domain.ChatMembership.InvalidChatMembershipValue
import com.convergencelabs.convergence.server.datastore.domain.ChatType.InvalidChatTypeValue
import com.convergencelabs.convergence.server.datastore.domain.{ChatMembership, ChatType}
import com.convergencelabs.convergence.server.domain.chat.ChatManagerActor._
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatManagerActor, GroupPermissions, UserPermissions}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId, DomainUserSessionId}
import com.google.protobuf.timestamp.Timestamp
import grizzled.slf4j.Logging
import org.json4s.JsonAST.JString
import scalapb.GeneratedMessage

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class ChatClientActor private(context: ActorContext[ChatClientActor.Message],
                              domainId: DomainId,
                              chatShardRegion: ActorRef[ChatActor.Message],
                              chatManagerActor: ActorRef[ChatManagerActor.Message],
                              clientActor: ActorRef[ClientActor.SendServerMessage],
                              session: DomainUserSessionId,
                              implicit val requestTimeout: Timeout)
  extends AbstractBehavior[ChatClientActor.Message](context) with Logging {

  import ChatClientActor._

  implicit val ec: ExecutionContextExecutor = context.executionContext
  implicit val s: ActorSystem[_] = context.system

  private[this] val outgoingSelf = context.self.narrow[ChatClientActor.OutgoingMessage]

  override def onMessage(msg: Message): Behavior[Message] = {
   msg match {
     case IncomingProtocolRequest(message, replyPromise) =>
       onRequestReceived(message, replyPromise)
     case IncomingProtocolPermissionsRequest(message, replyPromise) =>
       onPermissionsRequestReceived(message, replyPromise)
     case message: OutgoingMessage =>
       handleBroadcastMessage(message)
   }

    Behaviors.same
  }


  private[this] def handleBroadcastMessage(message: OutgoingMessage): Unit = {
    val serverMessage: GeneratedMessage with ServerMessage with NormalMessage = message match {
      // Broadcast messages
      case RemoteChatMessage(chatId, eventNumber, timestamp, session, message) =>
        RemoteChatMessageMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)),
          session.sessionId,
          message)

      case EventsMarkedSeen(chatId: String, eventNumber: Long, session: DomainUserSessionId) =>
        ChatEventsMarkedSeenMessage(chatId, Some(domainUserIdToData(session.userId)), eventNumber)

      case UserJoinedChat(chatId, eventNumber, timestamp, userId) =>
        UserJoinedChatMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId))

      case UserLeftChat(chatId, eventNumber, timestamp, userId) =>
        UserLeftChatMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId))

      case UserAddedToChannel(chatId, eventNumber, timestamp, userId, addedUser) =>
        UserAddedToChatChannelMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId), Some(addedUser))

      case UserRemovedFromChannel(chatId, eventNumber, timestamp, userId, removedUser) =>
        UserRemovedFromChatChannelMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId), Some(removedUser))

      case ChannelRemoved(chatId) =>
        ChatRemovedMessage(chatId)

      case ChatNameChanged(chatId, eventNumber, timestamp, userId, name) =>
        ChatNameSetMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId), name)

      case ChatTopicChanged(chatId, eventNumber, timestamp, userId, topic) =>
        ChatTopicSetMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(userId), topic)
    }

    clientActor ! ClientActor.SendServerMessage(serverMessage)
  }

  //
  // Incoming Messages
  //

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
        onGetJoinedChannels(message, replyCallback)
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
      chatManagerActor.ask[CreateChatResponse](CreateChatRequest(chatId, session.userId, t, m, Some(name), Some(topic), members, _)) onComplete {
        case Success(CreateChatSuccess(chatId)) =>
          cb.reply(CreateChatResponseMessage(chatId))
        case Success(RequestFailure(cause: ChatActor.ChatException)) =>
          this.handleChatChannelException(cause, cb)
        case Failure(cause) =>
          error("could not create chat: " + message, cause)
          cb.unexpectedError("An unexpected error occurred creating the chat")
      }
    }) recover {
      case InvalidChatTypeValue(value) =>
        cb.unexpectedError("Invalid chat type: " + value)
      case InvalidChatMembershipValue(value) =>
        cb.unexpectedError("Invalid chat membership: " + value)
      case cause =>
        error("Unexpected error creating chat", cause)
        cb.unknownError()
    }
  }

  private[this] def onRemoveChannel(message: RemoveChatRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveChatRequestMessage(chatId, _) = message
    // TODO improve th typing of this.
    val f: Future[_] = chatShardRegion.ask[ChatActor.RemoveChatResponse](ChatActor.RemoveChatRequest(domainId, chatId, session.userId, _))
    handleSimpleChannelRequest(message, f, () => RemoveChatResponseMessage(), cb)
  }

  private[this] def onJoinChannel(message: JoinChatRequestMessage, cb: ReplyCallback): Unit = {
    val JoinChatRequestMessage(chatId, _) = message
    chatShardRegion.ask[ChatActor.JoinChatResponse](ChatActor.JoinChatRequest(domainId, chatId, session, outgoingSelf, _)) onComplete {
      case Success(ChatActor.JoinChatSuccess(info)) =>
        cb.reply(JoinChatResponseMessage(Some(channelInfoToMessage(info))))
      case Success(ChatActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onLeaveChannel(message: LeaveChatRequestMessage, cb: ReplyCallback): Unit = {
    val LeaveChatRequestMessage(chatId, _) = message
    val f: Future[_] = chatShardRegion.ask[ChatActor.LeaveChatResponse](ChatActor.LeaveChatRequest(domainId, chatId, session, outgoingSelf, _))
    handleSimpleChannelRequest(message, f, () => LeaveChatResponseMessage(), cb)
  }

  private[this] def onAddUserToChannel(message: AddUserToChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val AddUserToChatChannelRequestMessage(chatId, userToAdd, _) = message
    val f: Future[_] = chatShardRegion.ask[ChatActor.AddUserToChatResponse](
      ChatActor.AddUserToChatRequest(domainId, chatId, session, ImplicitMessageConversions.dataToDomainUserId(userToAdd.get), _))
    handleSimpleChannelRequest(message, f, () => AddUserToChatChannelResponseMessage(), cb)
  }

  private[this] def onRemoveUserFromChannel(message: RemoveUserFromChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveUserFromChatChannelRequestMessage(chatId, userToRemove, _) = message
    val f: Future[_] = chatShardRegion.ask[ChatActor.RemoveUserFromChatResponse](
      ChatActor.RemoveUserFromChatRequest(domainId, chatId, session, ImplicitMessageConversions.dataToDomainUserId(userToRemove.get), _))
    handleSimpleChannelRequest(message, f, () => RemoveUserFromChatChannelResponseMessage(), cb)
  }

  private[this] def onSetChatChannelName(message: SetChatNameRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatNameRequestMessage(chatId, name, _) = message
    val f: Future[_] = chatShardRegion.ask[ChatActor.SetChatNameResponse](
      ChatActor.SetChatNameRequest(domainId, chatId, session.userId, name, _))
    handleSimpleChannelRequest(message, f, () => SetChatNameResponseMessage(), cb)
  }

  private[this] def onSetChatChannelTopic(message: SetChatTopicRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatTopicRequestMessage(chatId, topic, _) = message
    val f: Future[_] = chatShardRegion.ask[ChatActor.SetChatTopicResponse](
      ChatActor.SetChatTopicRequest(domainId, chatId, session.userId, topic, _))
    handleSimpleChannelRequest(message, f, () => SetChatTopicResponseMessage(), cb)
  }

  private[this] def onMarkEventsSeen(message: MarkChatEventsSeenRequestMessage, cb: ReplyCallback): Unit = {
    val MarkChatEventsSeenRequestMessage(chatId, eventNumber, _) = message
    val f: Future[_] = chatShardRegion.ask[ChatActor.MarkChatsEventsSeenResponse](
      ChatActor.MarkChatsEventsSeenRequest(domainId, chatId, session, eventNumber, _))
    handleSimpleChannelRequest(message, f, () => MarkChatEventsSeenResponseMessage(), cb)
  }

  private[this] def onAddChatPermissions(message: AddPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val AddPermissionsRequestMessage(_, id, worldPermissionData, userPermissionData, groupPermissionData, _) = message
    val groupPermissions = mapGroupPermissions(groupPermissionData)
    val userPermissions = mapUserPermissions(userPermissionData)
    val f: Future[_] = chatShardRegion.ask[ChatActor.AddChatPermissionsResponse](
      ChatActor.AddChatPermissionsRequest(domainId, id, session, Some(worldPermissionData.toSet), Some(userPermissions), Some(groupPermissions), _))
    handleSimpleChannelRequest(message, f, () => AddPermissionsResponseMessage(), cb)
  }

  private[this] def onRemoveChatPermissions(message: RemovePermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val RemovePermissionsRequestMessage(_, id, worldPermissionData, userPermissionData, groupPermissionData, _) = message
    val groupPermissions = mapGroupPermissions(groupPermissionData)
    val userPermissions = mapUserPermissions(userPermissionData)
    val f: Future[_] = chatShardRegion.ask[ChatActor.RemoveChatPermissionsResponse](
      ChatActor.RemoveChatPermissionsRequest(domainId, id, session, Some(worldPermissionData.toSet), Some(userPermissions), Some(groupPermissions), _))
    handleSimpleChannelRequest(message, f, () => RemovePermissionsResponseMessage(), cb)
  }


  private[this] def onSetChatPermissions(message: SetPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val SetPermissionsRequestMessage(_, id, worldPermissionData, userPermissionData, groupPermissionData, _) = message
    val groupPermissions = mapGroupPermissions(groupPermissionData)
    val userPermissions = mapUserPermissions(userPermissionData)
    val f: Future[_] = chatShardRegion.ask[ChatActor.SetChatPermissionsResponse](
      ChatActor.SetChatPermissionsRequest(domainId, id, session, Some(worldPermissionData.toSet), Some(userPermissions), Some(groupPermissions), _))
    handleSimpleChannelRequest(message, f, () => SetPermissionsResponseMessage(), cb)
  }

  private[this] def onGetClientChatPermissions(message: GetClientPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetClientPermissionsRequestMessage(_, id, _) = message
    chatShardRegion.ask[ChatActor.GetClientChatPermissionsResponse](ChatActor.GetClientChatPermissionsRequest(domainId, id, session, _)) onComplete {
      case Success(ChatActor.GetClientChatPermissionsSuccess(permissions)) =>
        cb.reply(GetClientPermissionsResponseMessage(permissions.toSeq))
      case Success(ChatActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatActor.RequestFailure(cause: Throwable)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onGetWorldPermissions(message: GetWorldPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetWorldPermissionsRequestMessage(_, id, _) = message
    chatShardRegion.ask[ChatActor.GetWorldChatPermissionsResponse](ChatActor.GetWorldChatPermissionsRequest(domainId, id, session, _)) onComplete {
      case Success(ChatActor.GetWorldChatPermissionsSuccess(permissions)) =>
        cb.reply(GetWorldPermissionsResponseMessage(permissions.toSeq))
      case Success(ChatActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatActor.RequestFailure(cause: Throwable)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onGetAllUserPermissions(message: GetAllUserPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetAllUserPermissionsRequestMessage(_, id, _) = message
    chatShardRegion.ask[ChatActor.GetAllUserChatPermissionsResponse](ChatActor.GetAllUserChatPermissionsRequest(domainId, id, session, _)) onComplete {
      case Success(ChatActor.GetAllUserChatPermissionsSuccess(users)) =>
        val userPermissionEntries = users.map { case (userId, permissions) =>
          (userId, UserPermissionsEntry(Some(ImplicitMessageConversions.domainUserIdToData(userId)), permissions.toSeq))
        }
        cb.reply(GetAllUserPermissionsResponseMessage(userPermissionEntries.values.toSeq))
      case Success(ChatActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatActor.RequestFailure(cause: Throwable)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onGetAllGroupPermissions(message: GetAllGroupPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetAllGroupPermissionsRequestMessage(_, id, _) = message
    chatShardRegion.ask[ChatActor.GetAllGroupChatPermissionsResponse](ChatActor.GetAllGroupChatPermissionsRequest(domainId, id, session, _)) onComplete {
      case Success(ChatActor.GetAllGroupChatPermissionsSuccess(groups)) =>
        cb.reply(GetAllGroupPermissionsResponseMessage(groups map { case (key, value) => (key, PermissionsList(value.toSeq)) }))
      case Success(ChatActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatActor.RequestFailure(cause: Throwable)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onGetUserPermissions(message: GetUserPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetUserPermissionsRequestMessage(_, id, user, _) = message
    chatShardRegion.ask[ChatActor.GetUserChatPermissionsResponse](ChatActor.GetUserChatPermissionsRequest(
      domainId, id, session, ImplicitMessageConversions.dataToDomainUserId(user.get), _)) onComplete {
      case Success(ChatActor.GetUserChatPermissionsSuccess(permissions)) =>
        cb.reply(GetUserPermissionsResponseMessage(permissions.toSeq))
      case Success(ChatActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatActor.RequestFailure(cause: Throwable)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onGetGroupPermissions(message: GetGroupPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetGroupPermissionsRequestMessage(_, id, groupId, _) = message
    chatShardRegion.ask[ChatActor.GetGroupChatPermissionsResponse](ChatActor.GetGroupChatPermissionsRequest(domainId, id, session, groupId, _)) onComplete {
      case Success(ChatActor.GetGroupChatPermissionsSuccess(permissions)) =>
        cb.reply(GetGroupPermissionsResponseMessage(permissions.toSeq))
      case Success(ChatActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatActor.RequestFailure(cause: Throwable)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onPublishMessage(message: PublishChatRequestMessage, cb: ReplyCallback): Unit = {
    val PublishChatRequestMessage(chatId, msg, _) = message
    chatShardRegion.ask[ChatActor.PublishChatMessageResponse](ChatActor.PublishChatMessageRequest(domainId, chatId, session, msg, _)) onComplete {
      case Success(ChatActor.PublishChatMessageSuccess(eventNumber, timestamp)) =>
        cb.reply(PublishChatResponseMessage(eventNumber, Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano))))
      case Success(ChatActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatActor.RequestFailure(cause)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onChannelsExist(message: ChatsExistRequestMessage, cb: ReplyCallback): Unit = {
    val ChatsExistRequestMessage(chatIds, _) = message
    chatManagerActor.ask[ChatManagerActor.ChatsExistsResponse](ChatsExistsRequest(session.userId, chatIds.toList, _)) onComplete {
      case Success(ChatManagerActor.ChatsExistsSuccess(channels)) =>
        cb.reply(ChatsExistResponseMessage(channels))
      case Success(ChatManagerActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatManagerActor.RequestFailure(cause)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onGetChannels(message: GetChatsRequestMessage, cb: ReplyCallback): Unit = {
    val GetChatsRequestMessage(ids, _) = message
    chatManagerActor.ask[ChatManagerActor.GetChatsResponse](GetChatsRequest(session.userId, ids.toSet, _)) onComplete {
      case Success(ChatManagerActor.GetChatsSuccess(channels)) =>
        val info = channels.map(channelInfoToMessage)
        cb.reply(GetChatsResponseMessage(info.toList))
      case Success(ChatManagerActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatManagerActor.RequestFailure(cause)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onGetDirect(message: GetDirectChatsRequestMessage, cb: ReplyCallback): Unit = {
    val GetDirectChatsRequestMessage(usernameLists, _) = message
    val usernames = usernameLists.map(_.values.map(ImplicitMessageConversions.dataToDomainUserId).toSet).toSet
    chatManagerActor.ask[ChatManagerActor.GetDirectChatsResponse](GetDirectChatsRequest(session.userId, usernames, _)) onComplete {
      case Success(ChatManagerActor.GetDirectChatsSuccess(channels)) =>
        val info = channels.map(channelInfoToMessage)
        cb.reply(GetDirectChatsResponseMessage(info.toList))
      case Success(ChatManagerActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatManagerActor.RequestFailure(cause)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onGetJoinedChannels(message: GetJoinedChatsRequestMessage, cb: ReplyCallback): Unit = {
    chatManagerActor.ask[ChatManagerActor.GetJoinedChatsResponse](GetJoinedChatsRequest(session.userId, _)) onComplete {
      case Success(ChatManagerActor.GetJoinedChatsSuccess(channels)) =>
        val info = channels.map(channelInfoToMessage)
        cb.reply(GetJoinedChatsResponseMessage(info.toList))
      case Success(ChatManagerActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatManagerActor.RequestFailure(cause)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
    }
  }

  private[this] def onGetHistory(message: ChatHistoryRequestMessage, cb: ReplyCallback): Unit = {
    val ChatHistoryRequestMessage(chatId, offset, limit, startEvent, forward, eventFilter, _) = message
    chatShardRegion.ask[ChatActor.GetChatHistoryResponse](
      ChatActor.GetChatHistoryRequest(domainId, chatId, Some(session), offset, limit, startEvent, forward, Some(eventFilter.toSet), None, _)
    ) onComplete {
      case Success(ChatActor.GetChatHistorySuccess(PagedData(events, startIndex, totalResults))) =>
        val eventData = events.map(channelEventToMessage)
        val reply = ChatHistoryResponseMessage(eventData, startIndex, totalResults)
        cb.reply(reply)
      case Success(ChatActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatActor.RequestFailure(cause)) =>
        handleUnexpectedError(message, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(message, cause, cb)
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

      chatManagerActor.ask[ChatManagerActor.ChatsSearchResponse](ChatsSearchRequest(searchTerm, searchFields, chatTypes, membership, offset, limit, _)) onComplete {
        case Success(ChatManagerActor.ChatsSearchSuccess(PagedData(chats, startIndex, totalResults))) =>
          val chatInfoData = chats.map(channelInfoToMessage)
          val reply = ChatsSearchResponseMessage(chatInfoData, startIndex, totalResults)
          cb.reply(reply)
        case Failure(cause) =>
          handleUnexpectedError(message, cause, cb)
      }
      ()
    }) recover {
      case InvalidChatTypeValue(value) =>
        cb.unexpectedError("Invalid chat type: " + value)
      case InvalidChatMembershipValue(value) =>
        cb.unexpectedError("Invalid chat membership: " + value)
      case cause =>
        error("Unexpected error searching chats", cause)
        cb.unknownError()
    }
  }

  private[this] def mapGroupPermissions(groupPermissionData: Map[String, PermissionsList]): Set[GroupPermissions] = {
    groupPermissionData.map {
      case (groupId, permissions) => (groupId, GroupPermissions(groupId, permissions.values.toSet))
    }.values.toSet
  }

  private[this] def mapUserPermissions(userPermissionData: Seq[UserPermissionsEntry]): Set[UserPermissions] = {
    userPermissionData
      .map(p => UserPermissions(ImplicitMessageConversions.dataToDomainUserId(p.user.get), p.permissions.toSet)).toSet
  }

  private[this] def handleSimpleChannelRequest(request: Any, f: Future[_], response: () => GeneratedMessage with ResponseMessage, cb: ReplyCallback): Unit = {
    f onComplete {
      case Success(ChatActor.RequestSuccess()) =>
        val r = response()
        cb.reply(r)
      case Success(ChatActor.RequestFailure(cause: ChatActor.ChatException)) =>
        handleChatChannelException(cause, cb)
      case Success(ChatActor.RequestFailure(cause)) =>
        handleUnexpectedError(request, cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def handleUnexpectedError(request: Any, cause: Throwable, cb: ReplyCallback): Unit = {
    error("Unexpected error processing chat request " + request, cause)
    cb.unexpectedError("Unexpected error processing chat request")
  }

  private[this] def handleChatChannelException(cause: ChatActor.ChatException, cb: ReplyCallback): Unit = {
    cause match {
      case ChatActor.ChatNotFoundException(chatId) =>
        cb.expectedError(
          ErrorCodes.ChatNotFound,
          s"Could not complete the request because a chat with id '$chatId' does not exist.",
          Map("chatId" -> JString(chatId)))
      case ChatActor.ChatNotJoinedException(chatId) =>
        cb.expectedError(
          ErrorCodes.ChatNotJoined,
          s"Could not complete the request the user is not joined to the chat: '$chatId'",
          Map("chatId" -> JString(chatId)))
      case ChatActor.ChatAlreadyExistsException(chatId) =>
        cb.expectedError(
          ErrorCodes.ChatAlreadyExists,
          s"Could not complete the request because a chat with id '$chatId' already exists.",
          Map("chatId" -> JString(chatId)))
      case ChatActor.ChatAlreadyJoinedException(chatId) =>
        cb.expectedError(
          ErrorCodes.ChatAlreadyJoined,
          s"Could not complete the request the user is already joined to the chat: '$chatId'",
          Map("chatId" -> JString(chatId)))
      case ChatActor.InvalidChatMessageException(message) =>
        cb.expectedError(
          ErrorCodes.InvalidChatMessage,
          s"The message that was sent was not valid for this type of chat: '$message'",
          Map())
    }
  }
}

object ChatClientActor {
  def apply(domain: DomainId,
            session: DomainUserSessionId,
            clientActor: ActorRef[ClientActor.SendServerMessage],
            chatShardRegion: ActorRef[ChatActor.Message],
            chatLookupActor: ActorRef[ChatManagerActor.Message],
            requestTimeout: Timeout): Behavior[Message] =
    Behaviors.setup(new ChatClientActor(_, domain, chatShardRegion, chatLookupActor, clientActor, session, requestTimeout))


  sealed trait Message extends CborSerializable

  //
  // Messages from the client
  //
  sealed trait IncomingMessage extends Message

  type IncomingRequestMessage = GeneratedMessage with RequestMessage with ChatMessage with ClientMessage

  case class IncomingProtocolRequest(message: IncomingRequestMessage, replyCallback: ReplyCallback) extends IncomingMessage

  case class IncomingProtocolPermissionsRequest(message: GeneratedMessage with RequestMessage with PermissionsMessage with ClientMessage, replyCallback: ReplyCallback) extends IncomingMessage


  //
  // Messages from the server
  //
  sealed trait OutgoingMessage extends Message {
    val chatId: String
  }

  case class UserJoinedChat(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId) extends OutgoingMessage

  case class UserLeftChat(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId) extends OutgoingMessage

  case class UserAddedToChannel(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, addedUserId: DomainUserId) extends OutgoingMessage

  case class UserRemovedFromChannel(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, removedUserId: DomainUserId) extends OutgoingMessage

  case class ChatNameChanged(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, name: String) extends OutgoingMessage

  case class ChatTopicChanged(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, topic: String) extends OutgoingMessage

  case class ChannelRemoved(chatId: String) extends OutgoingMessage

  case class RemoteChatMessage(chatId: String, eventNumber: Long, timestamp: Instant, session: DomainUserSessionId, message: String) extends OutgoingMessage

  case class EventsMarkedSeen(chatId: String, eventNumber: Long, session: DomainUserSessionId) extends OutgoingMessage

}
