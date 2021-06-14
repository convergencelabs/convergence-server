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

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.chat._
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection.ReplyCallback
import com.convergencelabs.convergence.server.api.realtime.protocol.ChatProtoConverters._
import com.convergencelabs.convergence.server.api.realtime.protocol.IdentityProtoConverters._
import com.convergencelabs.convergence.server.api.realtime.protocol.PermissionProtoConverters
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.PagedChatEvents
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatServiceActor._
import com.convergencelabs.convergence.server.backend.services.domain.chat._
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.chat.ChatMembership.InvalidChatMembershipValue
import com.convergencelabs.convergence.server.model.domain.chat.ChatType.InvalidChatTypeValue
import com.convergencelabs.convergence.server.model.domain.chat.{ChatMembership, ChatType}
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.google.protobuf.timestamp.Timestamp
import grizzled.slf4j.Logging
import scalapb.GeneratedMessage

import java.time.Instant
import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps
import scala.util.{Success, Try}

private final class ChatClientActor(context: ActorContext[ChatClientActor.Message],
                                    domainId: DomainId,
                                    chatShardRegion: ActorRef[ChatActor.Message],
                                    chatDeliveryShardRegion: ActorRef[ChatDeliveryActor.Message],
                                    chatManagerActor: ActorRef[ChatServiceActor.Message],
                                    clientActor: ActorRef[ClientActor.SendServerMessage],
                                    session: DomainSessionAndUserId,
                                    requestTimeout: Timeout)
  extends AbstractBehavior[ChatClientActor.Message](context) with Logging {

  import ChatClientActor._

  private[this] implicit val ec: ExecutionContextExecutor = context.executionContext
  private[this] implicit val s: ActorSystem[_] = context.system
  private[this] implicit val t: Timeout = requestTimeout

  private[this] val outgoingSelf = context.self.narrow[ChatClientActor.OutgoingMessage]

  chatDeliveryShardRegion ! ChatDeliveryActor.Subscribe(this.domainId, session.userId, outgoingSelf)

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
      case RemoteChatMessage(chatId, eventNumber, timestamp, user, message) =>
        RemoteChatMessageMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)),
          Some(domainUserIdToProto(user)),
          message)

      case EventsMarkedSeen(chatId: String, userId: DomainUserId, eventNumber: Long) =>
        ChatEventsMarkedSeenMessage(chatId, Some(domainUserIdToProto(userId)), eventNumber)

      case UserJoinedChat(chatId, eventNumber, timestamp, userId) =>
        UserJoinedChatMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(domainUserIdToProto(userId)))

      case UserLeftChat(chatId, eventNumber, timestamp, userId) =>
        UserLeftChatMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(domainUserIdToProto(userId)))

      case UserAddedToChat(chatId, eventNumber, timestamp, userId, addedUser) =>
        UserAddedToChatMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(domainUserIdToProto(userId)), Some(domainUserIdToProto(addedUser)))

      case UserRemovedFromChat(chatId, eventNumber, timestamp, userId, removedUser) =>
        UserRemovedFromChatMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(domainUserIdToProto(userId)), Some(domainUserIdToProto(removedUser)))

      case ChatRemoved(chatId) =>
        ChatRemovedMessage(chatId)

      case ChatNameChanged(chatId, eventNumber, timestamp, userId, name) =>
        ChatNameSetMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(domainUserIdToProto(userId)), name)

      case ChatTopicChanged(chatId, eventNumber, timestamp, userId, topic) =>
        ChatTopicSetMessage(chatId, eventNumber,
          Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), Some(domainUserIdToProto(userId)), topic)
    }

    clientActor ! ClientActor.SendServerMessage(serverMessage)
  }

  //
  // Incoming Messages
  //

  def onRequestReceived(message: RequestMessage with ChatMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case message: CreateChatRequestMessage =>
        onCreateChatRequest(message, replyCallback)
      case message: RemoveChatRequestMessage =>
        onRemoveChatRequest(message, replyCallback)
      case message: JoinChatRequestMessage =>
        onJoinChatRequest(message, replyCallback)
      case message: LeaveChatRequestMessage =>
        onLeaveChatRequest(message, replyCallback)
      case message: AddUserToChatRequestMessage =>
        onAddUserToChatRequest(message, replyCallback)
      case message: RemoveUserFromChatRequestMessage =>
        onRemoveUserFromChatRequest(message, replyCallback)
      case message: SetChatNameRequestMessage =>
        onSetChatChatName(message, replyCallback)
      case message: SetChatTopicRequestMessage =>
        onSetChatChatTopic(message, replyCallback)
      case message: MarkChatEventsSeenRequestMessage =>
        onMarkEventsSeen(message, replyCallback)
      case message: GetChatsRequestMessage =>
        onGetChat(message, replyCallback)
      case _: GetJoinedChatsRequestMessage =>
        onGetJoinedChats(replyCallback)
      case message: GetDirectChatsRequestMessage =>
        onGetDirect(message, replyCallback)
      case message: ChatHistoryRequestMessage =>
        onGetHistory(message, replyCallback)
      case message: PublishChatRequestMessage =>
        onPublishMessage(message, replyCallback)
      case message: ChatsExistRequestMessage =>
        onChatsExist(message, replyCallback)
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
      case message: GetConnectedUserPermissionsRequestMessage =>
        onGetConnectedUserPermissionsRequestMessage(message, replyCallback)
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

  private[this] def onCreateChatRequest(message: CreateChatRequestMessage, cb: ReplyCallback): Unit = {
    val CreateChatRequestMessage(chatId, chatType, membership, name, topic, memberData, _) = message
    val members = memberData.toSet.map(protoToDomainUserId)
    (for {
      ct <- ChatType.parse(chatType)
      m <- ChatMembership.parse(membership)
    } yield {
      chatManagerActor
        .ask[CreateChatResponse](CreateChatRequest(
          domainId, chatId, session.userId, ct, m, Some(name), Some(topic), members, _))
        .map(_.chatId.fold(
          {
            case ChatAlreadyExists() =>
              cb.expectedError(ErrorCodes.ChatAlreadyExists, s"A chat with id $chatId already exists")
            case UnknownError() =>
              cb.unknownError()
          },
          { chatId =>
            cb.reply(CreateChatResponseMessage(chatId))
          }))
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

  private[this] def onRemoveChatRequest(message: RemoveChatRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveChatRequestMessage(chatId, _) = message
    chatShardRegion
      .ask[ChatActor.RemoveChatResponse](ChatActor.RemoveChatRequest(domainId, chatId, session.userId, _))
      .map(_.response.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error, cb)
        },
        _ => cb.reply(RemoveChatResponseMessage())
      ))
      .recover(_ => cb.timeoutError())
  }


  private[this] def onJoinChatRequest(message: JoinChatRequestMessage, cb: ReplyCallback): Unit = {
    val JoinChatRequestMessage(chatId, _) = message
    chatShardRegion
      .ask[ChatActor.JoinChatResponse](ChatActor.JoinChatRequest(domainId, chatId, session.userId, outgoingSelf, _))
      .map(_.info.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error, cb)
          case ChatActor.ChatOperationNotSupported(reason) =>
            notSupported(reason, cb)
          case ChatActor.ChatAlreadyJoinedError() =>
            cb.expectedError(ErrorCodes.ChatAlreadyJoined, "The current user or session is already joined to this chat")
        },
        { info =>
          cb.reply(JoinChatResponseMessage(Some(chatStateToProto(info))))
        }))
      .recover(_ => cb.timeoutError())
  }

  private[this] def onLeaveChatRequest(message: LeaveChatRequestMessage, cb: ReplyCallback): Unit = {
    val LeaveChatRequestMessage(chatId, _) = message
    chatShardRegion
      .ask[ChatActor.LeaveChatResponse](ChatActor.LeaveChatRequest(domainId, chatId, session.userId, outgoingSelf, _))
      .map(_.response.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error, cb)
          case ChatActor.ChatNotJoinedError() =>
            chatNotJoined(chatId, cb)
          case ChatActor.ChatOperationNotSupported(reason) =>
            cb.expectedError(ErrorCodes.NotSupported, reason)
        },
        _ => cb.reply(LeaveChatResponseMessage())
      ))
      .recover(_ => cb.timeoutError())
  }

  private[this] def onAddUserToChatRequest(message: AddUserToChatRequestMessage, cb: ReplyCallback): Unit = {
    val AddUserToChatRequestMessage(chatId, userToAdd, _) = message
    chatShardRegion
      .ask[ChatActor.AddUserToChatResponse](ChatActor.AddUserToChatRequest(domainId, chatId, session.userId, protoToDomainUserId(userToAdd.get), _))
      .map(_.response.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error, cb)
          case ChatActor.ChatNotJoinedError() =>
            chatNotJoined(chatId, cb)
          case ChatActor.AlreadyAMemberError() =>
            cb.expectedError(ErrorCodes.ChatAlreadyMember, "The specified users is already a member of this chat.")
          case ChatActor.ChatOperationNotSupported(reason) =>
            cb.expectedError(ErrorCodes.NotSupported, reason)
        },
        _ => AddUserToChatResponseMessage())
      )
      .recover(_ => cb.timeoutError())
  }

  private[this] def onRemoveUserFromChatRequest(message: RemoveUserFromChatRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveUserFromChatRequestMessage(chatId, userToRemove, _) = message
    chatShardRegion
      .ask[ChatActor.RemoveUserFromChatResponse](
        ChatActor.RemoveUserFromChatRequest(domainId, chatId, session.userId, protoToDomainUserId(userToRemove.get), _))
      .map(_.response.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error, cb)
          case ChatActor.ChatNotJoinedError() =>
            chatNotJoined(chatId, cb)
          case ChatActor.NotAMemberError() =>
            cb.expectedError(ErrorCodes.NotAlreadyMember, "The use to remove was not a member of the chat")
          case ChatActor.CantRemoveSelfError() =>
            cb.expectedError(ErrorCodes.CantRemoveSelf, "You can not remove yourself, instead leave.")
          case ChatActor.ChatOperationNotSupported(reason) =>
            cb.expectedError(ErrorCodes.NotSupported, reason)
        },
        _ => RemoveUserFromChatResponseMessage())
      )
      .recover(_ => cb.timeoutError())
  }

  private[this] def onSetChatChatName(message: SetChatNameRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatNameRequestMessage(chatId, name, _) = message
    chatShardRegion
      .ask[ChatActor.SetChatNameResponse](ChatActor.SetChatNameRequest(domainId, chatId, session.userId, name, _))
      .map(_.response.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error, cb)
          case ChatActor.ChatNotJoinedError() =>
            chatNotJoined(chatId, cb)
        },
        _ => SetChatNameResponseMessage())
      )
      .recover(_ => cb.timeoutError())

  }

  private[this] def onSetChatChatTopic(message: SetChatTopicRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatTopicRequestMessage(chatId, topic, _) = message
    chatShardRegion.
      ask[ChatActor.SetChatTopicResponse](ChatActor.SetChatTopicRequest(domainId, chatId, session.userId, topic, _))
      .map(_.response.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error, cb)
          case ChatActor.ChatNotJoinedError() =>
            chatNotJoined(chatId, cb)
        },
        _ => SetChatTopicResponseMessage())
      )
      .recover(_ => cb.timeoutError())
  }

  private[this] def onMarkEventsSeen(message: MarkChatEventsSeenRequestMessage, cb: ReplyCallback): Unit = {
    val MarkChatEventsSeenRequestMessage(chatId, eventNumber, _) = message
    chatShardRegion
      .ask[ChatActor.MarkChatsEventsSeenResponse](ChatActor.MarkChatsEventsSeenRequest(domainId, chatId, session.userId, eventNumber, _))
      .map(_.response.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error, cb)
          case ChatActor.ChatNotJoinedError() =>
            chatNotJoined(chatId, cb)
        },
        _ => MarkChatEventsSeenResponseMessage())
      )
      .recover(_ => cb.timeoutError())
  }

  private[this] def onAddChatPermissions(message: AddPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val AddPermissionsRequestMessage(target, worldPermissionData, userPermissionData, groupPermissionData, _) = message
    getChatIdFromPermissionTarget(target, cb).map { chatId =>
      val groupPermissions = PermissionProtoConverters.protoToGroupPermissions(groupPermissionData)
      val userPermissions = PermissionProtoConverters.protoToUserPermissions(userPermissionData)
      chatShardRegion
        .ask[ChatActor.AddChatPermissionsResponse](
          ChatActor.AddChatPermissionsRequest(domainId, chatId, session, Some(worldPermissionData.toSet), Some(userPermissions), Some(groupPermissions), _))
        .map(_.response.fold(
          {
            case error: ChatActor.CommonErrors =>
              handleCommonErrors(error, cb)
            case ChatActor.ChatNotJoinedError() =>
              chatNotJoined(chatId, cb)
          },
          _ => OkResponse())
        )
        .recover(_ => cb.timeoutError())
    }
  }

  private[this] def onRemoveChatPermissions(message: RemovePermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val RemovePermissionsRequestMessage(target, worldPermissionData, userPermissionData, groupPermissionData, _) = message
    val groupPermissions = PermissionProtoConverters.protoToGroupPermissions(groupPermissionData)
    val userPermissions = PermissionProtoConverters.protoToUserPermissions(userPermissionData)
    getChatIdFromPermissionTarget(target, cb).map { chatId =>
      chatShardRegion
        .ask[ChatActor.RemoveChatPermissionsResponse](
          ChatActor.RemoveChatPermissionsRequest(domainId, chatId, session, Some(worldPermissionData.toSet), Some(userPermissions), Some(groupPermissions), _))
        .map(_.response.fold(
          {
            case error: ChatActor.CommonErrors =>
              handleCommonErrors(error, cb)
            case ChatActor.ChatNotJoinedError() =>
              chatNotJoined(chatId, cb)
          },
          _ => OkResponse())
        )
        .recover(_ => cb.timeoutError())
    }
  }


  private[this] def onSetChatPermissions(message: SetPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val SetPermissionsRequestMessage(target, worldPermissionData, userPermissionData, groupPermissionData, _) = message
    val groupPermissions = PermissionProtoConverters.protoToGroupPermissions(groupPermissionData)
    val userPermissions = PermissionProtoConverters.protoToUserPermissions(userPermissionData)
    getChatIdFromPermissionTarget(target, cb).map { chatId =>
      chatShardRegion
        .ask[ChatActor.SetChatPermissionsResponse](
          ChatActor.SetChatPermissionsRequest(domainId, chatId, session, Some(worldPermissionData.toSet), Some(userPermissions), Some(groupPermissions), _))
        .map(_.response.fold(
          {
            case error: ChatActor.CommonErrors =>
              handleCommonErrors(error, cb)
            case ChatActor.ChatNotJoinedError() =>
              chatNotJoined(chatId, cb)
          },
          _ => OkResponse())
        )
        .recover(_ => cb.timeoutError())
    }
  }

  private[this] def onGetConnectedUserPermissionsRequestMessage(message: GetConnectedUserPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetConnectedUserPermissionsRequestMessage(target, _) = message
    getChatIdFromPermissionTarget(target, cb).map { chatId =>
      chatShardRegion
        .ask[ChatActor.GetClientChatPermissionsResponse](ChatActor.GetClientChatPermissionsRequest(domainId, chatId, session, _))
        .map(_.permissions.fold(
          {
            case error: ChatActor.CommonErrors =>
              handleCommonErrors(error, cb)
            case ChatActor.ChatNotJoinedError() =>
              chatNotJoined(chatId, cb)
          },
          permissions => cb.reply(GetConnectedUserPermissionsResponseMessage(permissions.toSeq))
        ))
        .recover(_ => cb.timeoutError())
    }
  }

  private[this] def onGetWorldPermissions(message: GetWorldPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetWorldPermissionsRequestMessage(target, _) = message
    getChatIdFromPermissionTarget(target, cb).map { chatId =>
      chatShardRegion
        .ask[ChatActor.GetWorldChatPermissionsResponse](ChatActor.GetWorldChatPermissionsRequest(domainId, chatId, session, _))
        .map(_.permissions.fold(
          {
            case error: ChatActor.CommonErrors =>
              handleCommonErrors(error, cb)
            case ChatActor.ChatNotJoinedError() =>
              chatNotJoined(chatId, cb)
          },
          permissions => cb.reply(GetWorldPermissionsResponseMessage(permissions.toSeq))
        ))
        .recover(_ => cb.timeoutError())
    }
  }

  private[this] def onGetAllUserPermissions(message: GetAllUserPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetAllUserPermissionsRequestMessage(target, _) = message
    getChatIdFromPermissionTarget(target, cb).map { chatId =>
      chatShardRegion
        .ask[ChatActor.GetAllUserChatPermissionsResponse](
          ChatActor.GetAllUserChatPermissionsRequest(domainId, chatId, session, _))
        .map(_.users.fold(
          {
            case error: ChatActor.CommonErrors =>
              handleCommonErrors(error, cb)
            case ChatActor.ChatNotJoinedError() =>
              chatNotJoined(chatId, cb)
          },
          { users =>
            val userPermissionEntries = users.map { case (userId, permissions) =>
              (userId, UserPermissionsEntry(Some(domainUserIdToProto(userId)), permissions.toSeq))
            }
            cb.reply(GetAllUserPermissionsResponseMessage(userPermissionEntries.values.toSeq))
          }))
        .recover(_ => cb.timeoutError())
    }
  }

  private[this] def onGetAllGroupPermissions(message: GetAllGroupPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetAllGroupPermissionsRequestMessage(target, _) = message
    getChatIdFromPermissionTarget(target, cb).map { chatId =>
      chatShardRegion
        .ask[ChatActor.GetAllGroupChatPermissionsResponse](ChatActor.GetAllGroupChatPermissionsRequest(domainId, chatId, session, _))
        .map(_.groups.fold(
          {
            case error: ChatActor.CommonErrors =>
              handleCommonErrors(error, cb)
            case ChatActor.ChatNotJoinedError() =>
              chatNotJoined(chatId, cb)
          },
          { groups =>
            cb.reply(GetAllGroupPermissionsResponseMessage(groups map { case (key, value) => (key, PermissionsList(value.toSeq)) }))
          }))
        .recover(_ => cb.timeoutError())
    }
  }

  private[this] def onGetUserPermissions(message: GetUserPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetUserPermissionsRequestMessage(target, user, _) = message
    getChatIdFromPermissionTarget(target, cb).map { chatId =>
      chatShardRegion
        .ask[ChatActor.GetUserChatPermissionsResponse](ChatActor.GetUserChatPermissionsRequest(
          domainId, chatId, session, protoToDomainUserId(user.get), _))
        .map(_.permissions.fold(
          {
            case error: ChatActor.CommonErrors =>
              handleCommonErrors(error, cb)
            case ChatActor.ChatNotJoinedError() =>
              chatNotJoined(chatId, cb)
          },
          { permissions =>
            cb.reply(GetUserPermissionsResponseMessage(permissions.toSeq))
          }))
    }
  }

  private[this] def onGetGroupPermissions(message: GetGroupPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetGroupPermissionsRequestMessage(target, groupId, _) = message
    getChatIdFromPermissionTarget(target, cb).map { chatId =>
      chatShardRegion
        .ask[ChatActor.GetGroupChatPermissionsResponse](ChatActor.GetGroupChatPermissionsRequest(domainId, chatId, session, groupId, _))
        .map(_.permissions.fold(
          {
            case error: ChatActor.CommonErrors =>
              handleCommonErrors(error, cb)
            case ChatActor.ChatNotJoinedError() =>
              chatNotJoined(chatId, cb)
          },
          { permissions =>
            cb.reply(GetGroupPermissionsResponseMessage(permissions.toSeq))
          }))
        .recover(_ => cb.timeoutError())
    }
  }

  private[this] def onPublishMessage(message: PublishChatRequestMessage, cb: ReplyCallback): Unit = {
    val PublishChatRequestMessage(chatId, msg, _) = message
    chatShardRegion
      .ask[ChatActor.PublishChatMessageResponse](ChatActor.PublishChatMessageRequest(domainId, chatId, session.userId, msg, _))
      .map(_.response.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error, cb)
          case ChatActor.ChatNotJoinedError() =>
            chatNotJoined(chatId, cb)
        },
        { case ChatActor.PublishChatMessageAck(eventNumber, timestamp) =>
          cb.reply(PublishChatResponseMessage(eventNumber, Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano))))
        }))
      .recover(_ => cb.timeoutError())
  }

  private[this] def onChatsExist(message: ChatsExistRequestMessage, cb: ReplyCallback): Unit = {
    val ChatsExistRequestMessage(chatIds, _) = message
    chatManagerActor
      .ask[ChatServiceActor.ChatsExistsResponse](ChatsExistsRequest(domainId, session.userId, chatIds.toList, _))
      .map(_.exists.fold(
        {
          case UnknownError() =>
            cb.unknownError()
        },
        { exists =>
          cb.reply(ChatsExistResponseMessage(exists))
        }))
      .recover(_ => cb.timeoutError())
  }

  private[this] def onGetChat(message: GetChatsRequestMessage, cb: ReplyCallback): Unit = {
    val GetChatsRequestMessage(ids, _) = message
    chatManagerActor
      .ask[ChatServiceActor.GetChatsResponse](GetChatsRequest(domainId, session.userId, ids.toSet, _))
      .map(_.chatInfo.fold(
        {
          case UnknownError() =>
            cb.unknownError()
        },
        { chatInfo =>
          val info = chatInfo.map(chatStateToProto)
          cb.reply(GetChatsResponseMessage(info.toList))
        }))
      .recover(_ => cb.timeoutError())
  }

  private[this] def onGetDirect(message: GetDirectChatsRequestMessage, cb: ReplyCallback): Unit = {
    val GetDirectChatsRequestMessage(usernameLists, _) = message
    val usernames = usernameLists.map(_.values.map(protoToDomainUserId).toSet).toSet
    chatManagerActor
      .ask[ChatServiceActor.GetDirectChatsResponse](GetDirectChatsRequest(domainId, session.userId, usernames, _))
      .map(_.chatInfo.fold(
        {
          case UnknownError() =>
            cb.unknownError()
        },
        { chatInfo =>
          val info = chatInfo.map(chatStateToProto)
          cb.reply(GetDirectChatsResponseMessage(info.toList))
        }))
      .recover(_ => cb.timeoutError())
  }

  private[this] def onGetJoinedChats(cb: ReplyCallback): Unit = {
    chatManagerActor
      .ask[ChatServiceActor.GetJoinedChatsResponse](GetJoinedChatsRequest(domainId, session.userId, _))
      .map(_.chatInfo.fold(
        {
          case UnknownError() =>
            cb.unknownError()
        },
        { chatInfo =>
          val info = chatInfo.map(chatStateToProto)
          cb.reply(GetJoinedChatsResponseMessage(info.toList))
        }))
      .recover(_ => cb.timeoutError())
  }

  private[this] def onGetHistory(message: ChatHistoryRequestMessage, cb: ReplyCallback): Unit = {
    val ChatHistoryRequestMessage(chatId, offset, limit, startEvent, forward, eventFilter, _) = message
    chatShardRegion
      .ask[ChatActor.GetChatHistoryResponse](
        ChatActor.GetChatHistoryRequest(
          domainId, chatId, Some(session), QueryOffset(offset), QueryLimit(limit), startEvent, forward, Some(eventFilter.toSet), None, _))
      .map(_.events.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error, cb)
          case ChatActor.ChatNotJoinedError() =>
            chatNotJoined(chatId, cb)
        },
        { case PagedChatEvents(events, startIndex, totalResults) =>
          val eventData = events.map(chatEventToProto)
          val reply = ChatHistoryResponseMessage(eventData, startIndex, totalResults)
          cb.reply(reply)
        }))
      .recover(_ => cb.timeoutError())
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

      chatManagerActor
        .ask[ChatServiceActor.ChatsSearchResponse](
          ChatsSearchRequest(
            domainId,
            searchTerm,
            searchFields,
            chatTypes,
            membership,
            QueryOffset(offset),
            QueryLimit(limit), _))
        .map(_.chats.fold(
          {
            case UnknownError() =>
              cb.unknownError()
          },
          { case PagedData(chats, startIndex, totalResults) =>
            val chatInfoData = chats.map(chatStateToProto)
            val reply = ChatsSearchResponseMessage(chatInfoData, startIndex, totalResults)
            cb.reply(reply)
          }))
        .recover(_ => cb.timeoutError())
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

  private[this] def getChatIdFromPermissionTarget(target: Option[PermissionTarget], cb: ReplyCallback): Option[String] = {
    target match {
      case Some(t) =>
        t.targetType match {
          case PermissionTarget.TargetType.Chat(target) =>
            Some(target.id)
          case _ =>
            cb.expectedError(ErrorCodes.InvalidMessage,  "The permission target was not set")
            None
        }
      case None =>
        cb.expectedError(ErrorCodes.InvalidMessage,  "The permission target was not set")
        None
    }
  }

  private[this] def handleCommonErrors(error: ChatActor.CommonErrors, cb: ReplyCallback): Unit = {
    error match {
      case ChatActor.ChatNotFoundError() =>
        cb.expectedError(ErrorCodes.ChatNotFound, "The specified chat does not exist.")
      case ChatActor.UnauthorizedError() =>
        cb.expectedError(ErrorCodes.Unauthorized, "not authorized")
      case ChatActor.UnknownError() =>
        cb.unknownError()
    }
  }

  private[this] def chatNotJoined(chatId: String, cb: ReplyCallback): Unit = {
    cb.expectedError(ErrorCodes.ChatNotJoined, s"The chat must be joined to perform the requested operation: $chatId")
  }

  private[this] def notSupported(reason: String, cb: ReplyCallback): Unit = {
    cb.expectedError(ErrorCodes.NotSupported, reason)
  }
}

object ChatClientActor {
  private[realtime] def apply(domain: DomainId,
                              session: DomainSessionAndUserId,
                              clientActor: ActorRef[ClientActor.SendServerMessage],
                              chatShardRegion: ActorRef[ChatActor.Message],
                              chatDeliveryShardRegion: ActorRef[ChatDeliveryActor.Message],
                              chatManagerActor: ActorRef[ChatServiceActor.Message],
                              requestTimeout: Timeout): Behavior[Message] =
    Behaviors.setup(new ChatClientActor(_, domain, chatShardRegion, chatDeliveryShardRegion, chatManagerActor, clientActor, session, requestTimeout))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  //
  // Messages from the client
  //
  private[realtime] sealed trait IncomingMessage extends Message

  private[realtime] type IncomingRequestMessage = GeneratedMessage with RequestMessage with ChatMessage with ClientMessage

  private[realtime] final case class IncomingProtocolRequest(message: IncomingRequestMessage, replyCallback: ReplyCallback) extends IncomingMessage

  private[realtime] final case class IncomingProtocolPermissionsRequest(message: GeneratedMessage with RequestMessage with PermissionsMessage with ClientMessage, replyCallback: ReplyCallback) extends IncomingMessage


  //
  // Messages from the server
  //
  sealed trait OutgoingMessage extends Message {
    val chatId: String
  }

  final case class UserJoinedChat(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId) extends OutgoingMessage

  final case class UserLeftChat(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId) extends OutgoingMessage

  final case class UserAddedToChat(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, addedUserId: DomainUserId) extends OutgoingMessage

  final case class UserRemovedFromChat(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, removedUserId: DomainUserId) extends OutgoingMessage

  final case class ChatNameChanged(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, name: String) extends OutgoingMessage

  final case class ChatTopicChanged(chatId: String, eventNumber: Long, timestamp: Instant, userId: DomainUserId, topic: String) extends OutgoingMessage

  final case class ChatRemoved(chatId: String) extends OutgoingMessage

  final case class RemoteChatMessage(chatId: String, eventNumber: Long, timestamp: Instant, user: DomainUserId, message: String) extends OutgoingMessage

  final case class EventsMarkedSeen(chatId: String, user: DomainUserId, eventNumber: Long) extends OutgoingMessage

}
