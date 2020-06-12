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

package com.convergencelabs.convergence.server.domain.chat.processors.event

import java.time.Instant

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.datastore.domain.{ChatMember, ChatMembership, ChatType, ChatUserJoinedEvent}
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{CommonErrors, JoinChatRequest, JoinChatResponse}
import com.convergencelabs.convergence.server.domain.chat.processors.{MessageReplyTask, ReplyAndBroadcastTask}
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatState}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Success, Try, Failure}

class ChatEventMessageProcessorSpec extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with MockitoSugar {

  private val domainId = DomainId("ns", "d")
  private val chatId = "chatId"

  private val requester = DomainUserId.normal("requester")

  private val user1 = DomainUserId.normal("user1")

  private val user2 = DomainUserId.normal("user2")
  private val user3 = DomainUserId.normal("user3")

  private val member1 = ChatMember(chatId, user1, 0)
  private val member2 = ChatMember(chatId, user2, 0)
  private val member3 = ChatMember(chatId, user3, 0)

  private val state = ChatState(chatId,
    ChatType.Channel,
    Instant.now(),
    ChatMembership.Public,
    "chat name",
    "chat topic",
    Instant.now(),
    1,
    Map(user1 -> member1, user2 -> member2))

  private val validMessage = (_: JoinChatRequest, _: ChatState) => Right(())
  private val invalidMessage = (_: JoinChatRequest, _: ChatState) => Left(JoinChatResponse(Left(ChatActor.ChatAlreadyJoinedError())))

  private val truePermissions = (_: DomainUserId) => Success(true)
  private val falsePermissions = (_: DomainUserId) => Success(false)
  private val failPermissions = (_: DomainUserId) => Failure(new IllegalArgumentException("Induced error for testing"))


  private val createEvent = (msg: JoinChatRequest, state: ChatState) => ChatUserJoinedEvent(
    state.lastEventNumber + 1, state.id, msg.requester, Instant.now())

  private val successProcessEvent = (_: ChatUserJoinedEvent) => Success(())
  private val failureProcessEvent = (_: ChatUserJoinedEvent) => Failure(new IllegalArgumentException("Induced error for testing"))

  private val newState = state.copy(lastEventNumber = state.lastEventNumber + 1)
  private val updateState = (e: ChatUserJoinedEvent, state: ChatState) => newState

  private def createSuccessReply(r: JoinChatRequest, e: ChatUserJoinedEvent, state: ChatState): ReplyAndBroadcastTask = {
    val info = JoinEventProcessor.stateToInfo(state)
    ReplyAndBroadcastTask(MessageReplyTask(r.replyTo, JoinChatResponse(Right(info))), None)
  }

  private val createErrorReply = (e: CommonErrors) => JoinChatResponse(Left(e))
  "A processing a chat event" when {
    "processing a chat event request" must {

      //
      // Happy path
      //

      "create the correct reply for a successful execution" in {
        val Fixture(replyTo, message) = fixture()

        val result = process(
          message = message
        )

        result.newState shouldBe Some(newState)
        result.task.reply.replyTo shouldBe replyTo.ref

        val info = JoinEventProcessor.stateToInfo(result.newState.get)
        result.task.reply.response shouldBe JoinChatResponse(Right(info))

        result.task.broadcast shouldBe None
      }

      //
      // checkPermissions conditions
      //
      "correctly handle a request without proper permissions" in {
        val Fixture(replyTo, message) = fixture()

        val result = process(
          message = message,
          checkPermissions = falsePermissions
        )

        result.newState shouldBe None
        result.task.reply.replyTo shouldBe replyTo.ref

        result.task.reply.response shouldBe JoinChatResponse(Left(ChatActor.UnauthorizedError()))

        result.task.broadcast shouldBe None
      }
    }

    "correctly handle a failed permissions check" in {
      val Fixture(replyTo, message) = fixture()

      val result = process(
        message = message,
        checkPermissions = failPermissions
      )

      result.newState shouldBe None
      result.task.reply.replyTo shouldBe replyTo.ref

      result.task.reply.response shouldBe JoinChatResponse(Left(ChatActor.UnknownError()))

      result.task.broadcast shouldBe None
    }

    //
    // validateMessage conditions
    //
    "correctly handle an invalid message" in {
      val Fixture(replyTo, message) = fixture()

      val result = process(
        message = message,
        validateMessage = invalidMessage
      )

      result.newState shouldBe None
      result.task.reply.replyTo shouldBe replyTo.ref

      result.task.reply.response shouldBe JoinChatResponse(Left(ChatActor.ChatAlreadyJoinedError()))

      result.task.broadcast shouldBe None
    }

    //
    // processEvent conditions
    //
    "correctly handle an failure processing the chat event" in {
      val Fixture(replyTo, message) = fixture()

      val result = process(
        message = message,
        processEvent = failureProcessEvent
      )

      result.newState shouldBe None
      result.task.reply.replyTo shouldBe replyTo.ref

      result.task.reply.response shouldBe JoinChatResponse(Left(ChatActor.UnknownError()))

      result.task.broadcast shouldBe None
    }
  }

  private def process(message: JoinChatRequest,
                      state: ChatState = state,
                      checkPermissions: DomainUserId => Try[Boolean] = truePermissions,
                      validateMessage: (JoinChatRequest, ChatState) => Either[JoinChatResponse, Unit] = validMessage,
                      createEvent: (JoinChatRequest, ChatState) => ChatUserJoinedEvent = createEvent,
                      processEvent: ChatUserJoinedEvent => Try[Unit] = successProcessEvent,
                      updateState: (ChatUserJoinedEvent, ChatState) => ChatState = updateState,
                      createSuccessReply: (JoinChatRequest, ChatUserJoinedEvent, ChatState) => ReplyAndBroadcastTask = createSuccessReply,
                      createErrorReply: CommonErrors => JoinChatResponse = createErrorReply): ChatEventMessageProcessorResult = {
    ChatEventMessageProcessor.process(message,
      state,
      checkPermissions,
      validateMessage,
      createEvent,
      processEvent,
      updateState,
      createSuccessReply,
      createErrorReply)
  }

  private case class Fixture(replyTo: TestProbe[JoinChatResponse], message: JoinChatRequest)

  private def fixture(): Fixture = {
    val client = TestProbe[ChatClientActor.OutgoingMessage]
    val replyTo = TestProbe[JoinChatResponse]
    val message = JoinChatRequest(domainId, chatId, requester, client.ref, replyTo.ref)
    Fixture(replyTo, message)
  }
}


