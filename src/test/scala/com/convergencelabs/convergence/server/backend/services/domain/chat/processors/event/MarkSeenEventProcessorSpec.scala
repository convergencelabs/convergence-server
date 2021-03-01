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

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.InducedTestingException
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.backend.datastore.domain.chat.ChatStore
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.PermissionsStore
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor._
import com.convergencelabs.convergence.server.model.domain.chat.ChatMember
import org.mockito.{Matchers, Mockito}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Failure, Success}

class MarkSeenEventProcessorSpec extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with MockitoSugar
  with TestConstants {

  "An MarkSeenEventProcessor" when {

    "processing a request" must {
      "return success if the user is a member" in {
        val message = createMessage(member1.userId, 5L)

        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.markSeen(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Success(()))

        val permissionsStore = mock[PermissionsStore]
        Mockito.when(permissionsStore.userHasPermission(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Success(true))

        val response = MarkSeenEventProcessor.execute(message, state, chatStore, permissionsStore)
        response.newState shouldBe defined
        response.task.broadcast shouldBe defined
        response.task.reply.replyTo shouldBe message.replyTo
        response.task.reply.response shouldBe MarkChatsEventsSeenResponse(Right(Ok()))
      }
    }

    "validating a request" must {
      "return success if the user is a member" in {
        val message = createMessage(member1.userId, 5L)
        MarkSeenEventProcessor.validateMessage(message, state) shouldBe Right(())
      }

      "return a failure response if the user not a member" in {
        val message = createMessage(nonMember, 5L)
        MarkSeenEventProcessor.validateMessage(message, state) shouldBe
          Left(MarkChatsEventsSeenResponse(Left(ChatActor.ChatNotJoinedError())))
      }
    }

    "creating an event" must {
      "create a proper event" in {
        val message = createMessage(member1.userId, 5L)
        val event = MarkSeenEventProcessor.createEvent(message, state)

        event.chatId shouldBe state.id
        event.user shouldBe message.requester
        event.eventNumber shouldBe message.eventNumber
      }
    }

    "persisting an event" must {
      "succeed when the persistence operations succeed" in {
        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.markSeen(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Success(()))

        val permissionsStore = mock[PermissionsStore]

        val event = MarkSeen(state.id, member1.userId, 5L)

        val result = MarkSeenEventProcessor.persistEvent(chatStore, permissionsStore)(event)
        result shouldBe Success(())

        Mockito.verify(chatStore).markSeen(event.chatId, event.user, event.eventNumber)
      }

      "fail when the persistence operations fail" in {
        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.markSeen(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Failure(InducedTestingException()))

        val permissionsStore = mock[PermissionsStore]

        val event = MarkSeen(state.id, member1.userId, 5L)
        val result = MarkSeenEventProcessor.persistEvent(chatStore, permissionsStore)(event)
        result shouldBe Failure(InducedTestingException())
      }
    }

    "updating state" must {
      "compute the correct state" in {
        val event = MarkSeen(state.id, member1.userId, 5L)
        val newState = MarkSeenEventProcessor.updateState(event, state)
        newState shouldBe state.copy(
          members = state.members + (event.user -> ChatMember(state.id, event.user, 5L)),
        )
      }
    }

    "creating a success reply" must {
      "compute the correct reply" in {

        val message = createMessage(member1.userId, 5)

        val event = MarkSeen(state.id, message.requester, 5L)

        val task = MarkSeenEventProcessor.createSuccessReply(message, event, state)
        task.reply.replyTo shouldBe message.replyTo
        task.reply.response shouldBe MarkChatsEventsSeenResponse(Right(Ok()))
        task.broadcast shouldBe Some(ChatClientActor.EventsMarkedSeen(
          event.chatId, event.user, event.eventNumber))
      }
    }

    "creating an error reply" must {
      "compute the correct reply" in {
        val reply = MarkSeenEventProcessor.createErrorReply(UnknownError())
        reply shouldBe MarkChatsEventsSeenResponse(Left(UnknownError()))
      }
    }
  }

  private def createMessage(requester: DomainUserId, eventNumber: Long): MarkChatsEventsSeenRequest = {
    val replyTo = TestProbe[MarkChatsEventsSeenResponse]()
    MarkChatsEventsSeenRequest(domainId, chatId, requester, eventNumber, replyTo.ref)
  }
}


