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

package com.convergencelabs.convergence.server.backend.services.domain.chat.processors.event

import java.time.Instant

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.InducedTestingException
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.{CommonErrors, JoinChatRequest, JoinChatResponse}
import com.convergencelabs.convergence.server.backend.services.domain.chat.processors.{MessageReplyTask, ReplyAndBroadcastTask}
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor
import com.convergencelabs.convergence.server.model.domain.chat.{ChatState, ChatUserJoinedEvent}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Failure, Success, Try}

class ChatEventMessageProcessorSpec extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with MockitoSugar
  with TestConstants {

  private val validMessage = (_: JoinChatRequest, _: ChatState) => Right(())
  private val invalidMessage = (_: JoinChatRequest, _: ChatState) => Left(JoinChatResponse(Left(ChatActor.ChatAlreadyJoinedError())))

  private val truePermissions = (_: DomainUserId) => Success(true)
  private val falsePermissions = (_: DomainUserId) => Success(false)
  private val failPermissions = (_: DomainUserId) => Failure(InducedTestingException())


  private val createEvent = (msg: JoinChatRequest, state: ChatState) => ChatUserJoinedEvent(
    state.lastEventNumber + 1, state.id, msg.requester, Instant.now())

  private val successProcessEvent = (_: ChatUserJoinedEvent) => Success(())
  private val failureProcessEvent = (_: ChatUserJoinedEvent) => Failure(InducedTestingException())

  private val newState = state.copy(lastEventNumber = state.lastEventNumber + 1)
  private val updateState = (_: ChatUserJoinedEvent, _: ChatState) => newState

  private def createSuccessReply(r: JoinChatRequest, e: ChatUserJoinedEvent, state: ChatState): ReplyAndBroadcastTask[JoinChatResponse] = {
    ReplyAndBroadcastTask(MessageReplyTask(r.replyTo, JoinChatResponse(Right(state))), None)
  }

  private val createErrorReply = (e: CommonErrors) => JoinChatResponse(Left(e))

  "ChatEventMessageProcessor" when {

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

        result.task.reply.response shouldBe JoinChatResponse(Right(newState))

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
                      createSuccessReply: (JoinChatRequest, ChatUserJoinedEvent, ChatState) => ReplyAndBroadcastTask[JoinChatResponse] = createSuccessReply,
                      createErrorReply: CommonErrors => JoinChatResponse = createErrorReply): ChatEventMessageProcessorResult[JoinChatResponse] = {
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


