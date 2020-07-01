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

package com.convergencelabs.convergence.server.api.rest.domain

import java.time.Instant

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.api.rest.{okResponse, _}
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatManagerActor._
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.chat._
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}


private[domain] final class DomainChatService(domainRestActor: ActorRef[DomainRestActor.Message],
                                              chatSharding: ActorRef[ChatActor.Message],
                                              scheduler: Scheduler,
                                              executionContext: ExecutionContext,
                                              timeout: Timeout)
  extends AbstractDomainRestService(scheduler, executionContext, timeout) with Logging {

  import DomainChatService._

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("chats") {
      pathEnd {
        get {
          parameters("filter".?, "offset".as[Long].?, "limit".as[Long].?) { (filter, offset, limit) =>
            complete(getChats(domain, filter, offset, limit))
          }
        } ~ post {
          entity(as[CreateChatData]) { chatData =>
            complete(createChat(authProfile, domain, chatData))
          }
        }
      } ~ pathPrefix(Segment) { chatId =>
        pathEnd {
          get {
            complete(getChat(domain, chatId))
          } ~ delete {
            complete(deleteChat(authProfile, domain, chatId))
          }
        } ~ (path("name") & put) {
          entity(as[SetNameData]) { data =>
            complete(setName(authProfile, domain, chatId, data))
          }
        } ~ (path("topic") & put) {
          entity(as[SetTopicData]) { data =>
            complete(setTopic(authProfile, domain, chatId, data))
          }
        } ~ (path("events") & get) {
          parameters(
            "eventTypes".?,
            "messageFilter".?,
            "startEvent".as[Long].?,
            "offset".as[Long].?,
            "limit".as[Long].?,
            "forward".as[Boolean].?) { (eventTypes, messageFilter, startEvent, offset, limit, forward) =>
            complete(getChatEvents(domain, chatId, eventTypes, messageFilter, startEvent, offset, limit, forward))
          }
        }
      }
    }
  }

  private[this] def getChats(domain: DomainId, searchTerm: Option[String], offset: Option[Long], limit: Option[Long]): Future[RestResponse] = {
    domainRestActor
      .ask[ChatsSearchResponse](r =>
        DomainRestMessage(domain, ChatsSearchRequest(searchTerm, None, None, None, QueryOffset(offset), QueryLimit(limit), r)))
      .map(_.chats.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { case PagedData(chatInfo, offset, total) =>
          val data = chatInfo.map { chat => toChatStateData(chat) }
          val response = PagedRestResponse(data, offset, total)
          okResponse(response)
        }))
  }

  private[this] def getChat(domain: DomainId, chatId: String): Future[RestResponse] = {
    domainRestActor
      .ask[GetChatInfoResponse](r => DomainRestMessage(domain, GetChatInfoRequest(chatId, r)))
      .map(_.chat.fold(
        {
          case ChatNotFound() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        chat => okResponse(toChatStateData(chat))
      ))

  }

  private[this] def createChat(authProfile: AuthorizationProfile, domain: DomainId, chatData: CreateChatData): Future[RestResponse] = {
    val CreateChatData(chatId, chatType, membership, name, topic, members) = chatData

    domainRestActor
      .ask[CreateChatResponse] { r =>
        val request = CreateChatRequest(
          Some(chatId),
          DomainUserId.convergence(authProfile.username),
          ChatType.parse(chatType).get,
          ChatMembership.parse(membership).get,
          Some(name),
          Some(topic),
          members.map(DomainUserId.normal),
          r)
        DomainRestMessage(domain, request)
      }
      .map(_.chatId.fold(
        {
          case ChatAlreadyExists() =>
            duplicateResponse("chatId")
          case UnknownError() =>
            InternalServerError
        },
        chatId => createdResponse(chatId)
      ))
  }

  private[this] def deleteChat(authProfile: AuthorizationProfile, domain: DomainId, chatId: String): Future[RestResponse] = {
    chatSharding
      .ask[ChatActor.RemoveChatResponse](r =>
        ChatActor.RemoveChatRequest(domain, chatId, DomainUserId.convergence(authProfile.username), r))
      .map(_.response.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error)
        },
        _ => DeletedResponse
      ))
  }

  private[this] def setName(authProfile: AuthorizationProfile, domain: DomainId, chatId: String, data: SetNameData): Future[RestResponse] = {
    val SetNameData(name) = data
    val userId = DomainUserId.convergence(authProfile.username)
    chatSharding
      .ask[ChatActor.SetChatNameResponse](r =>
        ChatActor.SetChatNameRequest(domain, chatId, userId, name, r))
      .map(_.response.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error)
          case ChatActor.ChatNotJoinedError() =>
            ForbiddenError
        },
        _ => OkResponse
      ))
  }

  private[this] def setTopic(authProfile: AuthorizationProfile, domain: DomainId, chatId: String, data: SetTopicData): Future[RestResponse] = {
    val SetTopicData(topic) = data
    val userId = DomainUserId.convergence(authProfile.username)
    chatSharding
      .ask[ChatActor.SetChatTopicResponse](r =>
        ChatActor.SetChatTopicRequest(domain, chatId, userId, topic, r))
      .map(_.response.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error)
          case ChatActor.ChatNotJoinedError() =>
            ForbiddenError
        },
        _ => OkResponse
      ))
  }

  private[this] def getChatEvents(domain: DomainId,
                                  chatId: String,
                                  eventTypes: Option[String],
                                  messageFilter: Option[String],
                                  startEvent: Option[Long],
                                  offset: Option[Long],
                                  limit: Option[Long],
                                  forward: Option[Boolean]): Future[RestResponse] = {
    val types = eventTypes.map(t => t.split(",").toSet)
    chatSharding
      .ask[ChatActor.GetChatHistoryResponse](r =>
        ChatActor.GetChatHistoryRequest(domain, chatId, None, QueryOffset(offset), QueryLimit(limit), startEvent, forward, types, messageFilter, r))
      .map(_.events.fold(
        {
          case error: ChatActor.CommonErrors =>
            handleCommonErrors(error)
          case ChatActor.ChatNotJoinedError() =>
            ForbiddenError
        },
        { events =>
          val response = PagedRestResponse(events.data.map(toChatEventData), events.offset, events.count)
          okResponse(response)
        }
      ))
  }

  private[this] def toChatStateData(chatState: ChatState): ChatStateRestData = {
    val ChatState(id, chatType, created, membership, name, topic, lastEventTimestamp, lastEventNumber, members) = chatState
    ChatStateRestData(
      id,
      chatType.toString.toLowerCase,
      membership.toString.toLowerCase(),
      name,
      topic,
      members.keySet,
      created,
      lastEventNumber,
      lastEventTimestamp)
  }

  private[this] def toChatEventData(event: ChatEvent): ChatEventData = {
    event match {
      case ChatCreatedEvent(eventNumber, id, user, timestamp, name, topic, members) =>
        ChatCreatedEventData(eventNumber, id, user, timestamp, name, topic, members)

      case ChatUserJoinedEvent(eventNumber, id, user, timestamp) =>
        ChatUserJoinedEventData(eventNumber, id, user, timestamp)
      case ChatUserLeftEvent(eventNumber, id, user, timestamp) =>
        ChatUserLeftEventData(eventNumber, id, user, timestamp)

      case ChatUserAddedEvent(eventNumber, id, user, timestamp, userAdded) =>
        ChatUserAddedEventData(eventNumber, id, user, timestamp, userAdded)
      case ChatUserRemovedEvent(eventNumber, id, user, timestamp, userRemoved) =>
        ChatUserRemovedEventData(eventNumber, id, user, timestamp, userRemoved)

      case ChatTopicChangedEvent(eventNumber, id, user, timestamp, topic) =>
        ChatTopicChangedEventData(eventNumber, id, user, timestamp, topic)

      case ChatNameChangedEvent(eventNumber, id, user, timestamp, name) =>
        ChatNameChangedEventData(eventNumber, id, user, timestamp, name)

      case ChatMessageEvent(eventNumber, id, user, timestamp, message) =>
        ChatMessageEventData(eventNumber, id, user, timestamp, message)
    }
  }

  private[this] def handleCommonErrors(error: ChatActor.CommonErrors): RestResponse = {
    error match {
      case ChatActor.ChatNotFoundError() =>
        NotFoundResponse
      case ChatActor.UnauthorizedError() =>
        ForbiddenError
      case ChatActor.UnknownError() =>
        InternalServerError
    }
  }
}


object DomainChatService {

  final case class ChatStateRestData(chatId: String,
                                     chatType: String,
                                     membership: String,
                                     name: String,
                                     topic: String,
                                     members: Set[DomainUserId],
                                     created: Instant,
                                     lastEventNumber: Long,
                                     lastEventTimestamp: Instant)

  final case class CreateChatData(chatId: String, chatType: String, membership: String, name: String, topic: String, members: Set[String])

  final case class SetNameData(name: String)

  final case class SetTopicData(topic: String)

  sealed trait ChatEventData {
    val `type`: String
    val eventNumber: Long
    val id: String
    val user: DomainUserId
    val timestamp: Instant
  }

  final case class ChatCreatedEventData(eventNumber: Long,
                                        id: String,
                                        user: DomainUserId,
                                        timestamp: Instant,
                                        name: String,
                                        topic: String,
                                        members: Set[DomainUserId]) extends ChatEventData {
    val `type` = "created"
  }

  final case class ChatMessageEventData(eventNumber: Long,
                                        id: String,
                                        user: DomainUserId,
                                        timestamp: Instant,
                                        message: String) extends ChatEventData {
    val `type` = "message"
  }

  final case class ChatUserJoinedEventData(eventNumber: Long,
                                           id: String,
                                           user: DomainUserId,
                                           timestamp: Instant) extends ChatEventData {
    val `type` = "user_joined"
  }

  final case class ChatUserLeftEventData(eventNumber: Long,
                                         id: String,
                                         user: DomainUserId,
                                         timestamp: Instant) extends ChatEventData {
    val `type` = "user_left"
  }

  final case class ChatUserAddedEventData(eventNumber: Long,
                                          id: String,
                                          user: DomainUserId,
                                          timestamp: Instant,
                                          userAdded: DomainUserId) extends ChatEventData {
    val `type` = "user_added"
  }

  final case class ChatUserRemovedEventData(eventNumber: Long,
                                            id: String,
                                            user: DomainUserId,
                                            timestamp: Instant,
                                            userRemoved: DomainUserId) extends ChatEventData {
    val `type` = "user_removed"
  }

  final case class ChatNameChangedEventData(eventNumber: Long,
                                            id: String,
                                            user: DomainUserId,
                                            timestamp: Instant,
                                            name: String) extends ChatEventData {
    val `type` = "name_changed"
  }

  final case class ChatTopicChangedEventData(eventNumber: Long,
                                             id: String,
                                             user: DomainUserId,
                                             timestamp: Instant,
                                             topic: String) extends ChatEventData {
    val `type` = "topic_changed"
  }

}
