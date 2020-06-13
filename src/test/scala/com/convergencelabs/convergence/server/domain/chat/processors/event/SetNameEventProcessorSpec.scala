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
import com.convergencelabs.convergence.server.InducedTestingException
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.datastore.domain.{ChatNameChangedEvent, ChatStore, PermissionsStore}
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor
import com.convergencelabs.convergence.server.domain.chat.ChatActor._
import com.orientechnologies.orient.core.id.ORecordId
import org.mockito.{Matchers, Mockito}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Failure, Success}

class SetNameEventProcessorSpec extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with MockitoSugar
  with TestConstants {

  "An SetNameEventProcessor" when {

    "processing a request" must {
      "return success if the user is a member" in {
        val message = createMessage(member1.userId)

        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatNameChangedEvent(Matchers.any())).thenReturn(Success(()))
        Mockito.when(chatStore.getChatRid(Matchers.any())).thenReturn(Success(ORecordId.EMPTY_RECORD_ID))

        val permissionsStore = mock[PermissionsStore]
        Mockito.when(permissionsStore.hasPermissionForRecord(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Success(true))

        val response = SetNameEventProcessor.execute(message, state, chatStore, permissionsStore)
        response.newState shouldBe defined
        response.task.broadcast shouldBe defined
        response.task.reply.replyTo shouldBe message.replyTo
        val newState = response.newState.get
        response.task.reply.response shouldBe SetChatNameResponse(Right(()))
      }
    }

    "validating a request" must {
      "return success if the user is a member" in {
        val message = createMessage(member1.userId)
        SetNameEventProcessor.validateMessage(message, state) shouldBe Right(())
      }

      "return a failure response if the user not a member" in {
        val message = createMessage(nonMember)
        SetNameEventProcessor.validateMessage(message, state) shouldBe
          Left(SetChatNameResponse(Left(ChatActor.ChatNotJoinedError())))
      }
    }

    "creating an event" must {
      "create a proper event" in {
        val message = createMessage(member1.userId)
        val event = SetNameEventProcessor.createEvent(message, state)

        event.id shouldBe state.id
        event.user shouldBe message.requester
        event.name shouldBe message.name
        event.eventNumber shouldBe state.lastEventNumber + 1
        event.timestamp.compareTo(Instant.now()) <= 0 shouldBe true
        event.timestamp.compareTo(state.lastEventTime) >= 0 shouldBe true
      }
    }

    "persisting an event" must {
      "succeed when the persistence operations succeed" in {
        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatNameChangedEvent(Matchers.any())).thenReturn(Success(()))

        val permissionsStore = mock[PermissionsStore]

        val timestamp = Instant.now()
        val event = ChatNameChangedEvent(1L, state.id, member1.userId, timestamp, "test")

        val result = SetNameEventProcessor.persistEvent(chatStore, permissionsStore)(event)
        result shouldBe Success(())

        Mockito.verify(chatStore).addChatNameChangedEvent(event)
      }

      "fail when the persistence operations fail" in {
        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatNameChangedEvent(Matchers.any())).thenReturn(Failure(InducedTestingException()))

        val permissionsStore = mock[PermissionsStore]

        val timestamp = Instant.now()
        val event = ChatNameChangedEvent(1L, state.id, member1.userId, timestamp, "test")
        val result = SetNameEventProcessor.persistEvent(chatStore, permissionsStore)(event)
        result shouldBe Failure(InducedTestingException())
      }
    }

    "updating state" must {
      "compute the correct state" in {
        val timestamp = Instant.now()
        val event = ChatNameChangedEvent(1L, state.id, member1.userId, timestamp, "test")
        val newState = SetNameEventProcessor.updateState(event, state)
        newState shouldBe state.copy(
          name = event.name,
          lastEventNumber = event.eventNumber,
          lastEventTime = event.timestamp
        )
      }
    }

    "creating a success reply" must {
      "compute the correct reply" in {
        val message = createMessage(member1.userId)

        val timestamp = Instant.now()
        val event = ChatNameChangedEvent(1L, state.id, message.requester, timestamp, message.name)

        val task = SetNameEventProcessor.createSuccessReply(message, event, state)
        task.reply.replyTo shouldBe message.replyTo
        task.reply.response shouldBe SetChatNameResponse(Right(()))
        task.broadcast shouldBe Some(ChatClientActor.ChatNameChanged(
          event.id, event.eventNumber, event.timestamp, event.user, event.name))

      }
    }

    "creating an error reply" must {
      "compute the correct reply" in {
        val reply = SetNameEventProcessor.createErrorReply(UnknownError())
        reply shouldBe SetChatNameResponse(Left(UnknownError()))
      }
    }
  }

  private def createMessage(requester: DomainUserId): SetChatNameRequest = {
    val replyTo = TestProbe[SetChatNameResponse]
    SetChatNameRequest(domainId, chatId, requester, "test", replyTo.ref)
  }
}


