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
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.InducedTestingException
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.datastore.domain.{ChatStore, ChatTopicChangedEvent, PermissionsStore}
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor
import com.convergencelabs.convergence.server.domain.chat.ChatActor._
import org.mockito.{Matchers, Mockito}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Failure, Success}

class SetTopicEventProcessorSpec extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with MockitoSugar
  with TestConstants {

  "An SetTopicEventProcessor" when {

    "processing a request" must {
      "return success if the user is a member" in {
        val message = createMessage(member1.userId)

        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatTopicChangedEvent(Matchers.any())).thenReturn(Success(()))

        val permissionsStore = mock[PermissionsStore]
        Mockito.when(permissionsStore.userHasPermission(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Success(true))

        val response = SetTopicEventProcessor.execute(message, state, chatStore, permissionsStore)
        response.newState shouldBe defined
        response.task.broadcast shouldBe defined
        response.task.reply.replyTo shouldBe message.replyTo
        val newState = response.newState.get
        response.task.reply.response shouldBe SetChatTopicResponse(Right(Ok()))
      }
    }

    "validating a request" must {
      "return success if the user is a member" in {
        val message = createMessage(member1.userId)
        SetTopicEventProcessor.validateMessage(message, state) shouldBe Right(())
      }

      "return a failure response if the user not a member" in {
        val message = createMessage(nonMember)
        SetTopicEventProcessor.validateMessage(message, state) shouldBe
          Left(SetChatTopicResponse(Left(ChatActor.ChatNotJoinedError())))
      }
    }

    "creating an event" must {
      "create a proper event" in {
        val message = createMessage(member1.userId)
        val event = SetTopicEventProcessor.createEvent(message, state)

        event.id shouldBe state.id
        event.user shouldBe message.requester
        event.topic shouldBe message.topic
        event.eventNumber shouldBe state.lastEventNumber + 1
        event.timestamp.compareTo(Instant.now()) <= 0 shouldBe true
        event.timestamp.compareTo(state.lastEventTime) >= 0 shouldBe true
      }
    }

    "persisting an event" must {
      "succeed when the persistence operations succeed" in {
        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatTopicChangedEvent(Matchers.any())).thenReturn(Success(()))

        val permissionsStore = mock[PermissionsStore]

        val timestamp = Instant.now()
        val event = ChatTopicChangedEvent(1L, state.id, member1.userId, timestamp, "test")

        val result = SetTopicEventProcessor.persistEvent(chatStore, permissionsStore)(event)
        result shouldBe Success(())

        Mockito.verify(chatStore).addChatTopicChangedEvent(event)
      }

      "fail when the persistence operations fail" in {
        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatTopicChangedEvent(Matchers.any())).thenReturn(Failure(InducedTestingException()))

        val permissionsStore = mock[PermissionsStore]

        val timestamp = Instant.now()
        val event = ChatTopicChangedEvent(1L, state.id, member1.userId, timestamp, "test")
        val result = SetTopicEventProcessor.persistEvent(chatStore, permissionsStore)(event)
        result shouldBe Failure(InducedTestingException())
      }
    }

    "updating state" must {
      "compute the correct state" in {
        val timestamp = Instant.now()
        val event = ChatTopicChangedEvent(1L, state.id, member1.userId, timestamp, "test")
        val newState = SetTopicEventProcessor.updateState(event, state)
        newState shouldBe state.copy(
          topic = event.topic,
          lastEventNumber = event.eventNumber,
          lastEventTime = event.timestamp
        )
      }
    }

    "creating a success reply" must {
      "compute the correct reply" in {
        val message = createMessage(member1.userId)

        val timestamp = Instant.now()
        val event = ChatTopicChangedEvent(1L, state.id, message.requester, timestamp, message.topic)

        val task = SetTopicEventProcessor.createSuccessReply(message, event, state)
        task.reply.replyTo shouldBe message.replyTo
        task.reply.response shouldBe SetChatTopicResponse(Right(Ok()))
        task.broadcast shouldBe Some(ChatClientActor.ChatTopicChanged(
          event.id, event.eventNumber, event.timestamp, event.user, event.topic))

      }
    }

    "creating an error reply" must {
      "compute the correct reply" in {
        val reply = SetTopicEventProcessor.createErrorReply(UnknownError())
        reply shouldBe SetChatTopicResponse(Left(UnknownError()))
      }
    }
  }

  private def createMessage(requester: DomainUserId): SetChatTopicRequest = {
    val replyTo = TestProbe[SetChatTopicResponse]
    SetChatTopicRequest(domainId, chatId, requester, "test", replyTo.ref)
  }
}


