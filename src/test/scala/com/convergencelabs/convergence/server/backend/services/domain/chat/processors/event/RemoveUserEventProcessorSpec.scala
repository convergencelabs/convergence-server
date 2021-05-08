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
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.InducedTestingException
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.backend.datastore.domain.chat.ChatStore
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.PermissionsStore
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.ChatPermissionTarget
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.{RemoveUserFromChatResponse, _}
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatActor, ChatPermissions}
import com.convergencelabs.convergence.server.model.domain.chat.ChatUserRemovedEvent
import org.mockito.{Matchers, Mockito}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Failure, Success}

class RemoveUserEventProcessorSpec extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with MockitoSugar
  with TestConstants {

  "An RemoveUserEventProcessor" when {

    "processing a request" must {
      "return success if the user is not a member" in {
        val message = createMessage(member1.userId, member2.userId)

        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatUserRemovedEvent(Matchers.any())).thenReturn(Success(()))

        val permissionsStore = mock[PermissionsStore]
        Mockito.when(permissionsStore.userHasPermission(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Success(true))

        Mockito.when(permissionsStore.removeAllPermissionsForUserByTarget(Matchers.any(), Matchers.any()))
          .thenReturn(Success(()))

        val response = RemoveUserEventProcessor.execute(message, state, chatStore, permissionsStore)
        response.newState shouldBe defined
        response.task.broadcast shouldBe defined
        response.task.reply.replyTo shouldBe message.replyTo
        response.task.reply.response shouldBe RemoveUserFromChatResponse(Right(Ok()))
      }
    }

    "validating a request" must {
      "return success if the requester and user to remove are members" in {
        val message = createMessage(member1.userId, member2.userId)
        RemoveUserEventProcessor.validateMessage(message, state) shouldBe Right(())
      }

      "return a failure response if the user to remove is not a member" in {
        val message = createMessage(member1.userId, nonMember)
        RemoveUserEventProcessor.validateMessage(message, state) shouldBe
          Left(RemoveUserFromChatResponse(Left(ChatActor.NotAMemberError())))
      }

      "return a failure response if the user tries to remove themselves" in {
        val message = createMessage(member1.userId, member1.userId)
        RemoveUserEventProcessor.validateMessage(message, state) shouldBe
          Left(RemoveUserFromChatResponse(Left(ChatActor.CantRemoveSelfError())))
      }
    }

    "creating an event" must {
      "create a proper event" in {
        val message = createMessage(member1.userId, member2.userId)
        val event = RemoveUserEventProcessor.createEvent(message, state)

        event.id shouldBe state.id
        event.user shouldBe message.requester
        event.userRemoved shouldBe message.userToRemove
        event.eventNumber shouldBe state.lastEventNumber + 1
        event.timestamp.compareTo(Instant.now()) <= 0 shouldBe true
        event.timestamp.compareTo(state.lastEventTime) >= 0 shouldBe true
      }
    }

    "persisting an event" must {
      "succeed when the persistence operations succeed" in {
        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatUserRemovedEvent(Matchers.any())).thenReturn(Success(()))

        val permissionsStore = mock[PermissionsStore]
        Mockito.when(permissionsStore.removeAllPermissionsForUserByTarget(Matchers.any(), Matchers.any()))
          .thenReturn(Success(()))

        val timestamp = Instant.now()
        val event = ChatUserRemovedEvent(1L, state.id, member1.userId, timestamp, member2.userId)

        val result = RemoveUserEventProcessor.persistEvent(chatStore, permissionsStore)(event)
        result shouldBe Success(())


        Mockito.verify(chatStore).addChatUserRemovedEvent(event)
        Mockito.verify(permissionsStore).removeAllPermissionsForUserByTarget(event.userRemoved, ChatPermissionTarget(state.id))
      }

      "fail when the persistence operations fail" in {
        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatUserRemovedEvent(Matchers.any())).thenReturn(Failure(InducedTestingException()))

        val permissionsStore = mock[PermissionsStore]

        val timestamp = Instant.now()
        val event = ChatUserRemovedEvent(1L, state.id, member1.userId, timestamp, member2.userId)

        val result = RemoveUserEventProcessor.persistEvent(chatStore, permissionsStore)(event)
        result shouldBe Failure(InducedTestingException())
      }
    }

    "updating state" must {
      "compute the correct state" in {
        val timestamp = Instant.now()
        val event = ChatUserRemovedEvent(1L, state.id, member1.userId, timestamp, member2.userId)

        val newState = RemoveUserEventProcessor.updateState(event, state)
        newState shouldBe state.copy(
          members = state.members - event.userRemoved,
          lastEventNumber = event.eventNumber,
          lastEventTime = timestamp
        )
      }
    }

    "creating a success reply" must {
      "compute the correct reply" in {
        val message = createMessage(member1.userId, member2.userId)

        val timestamp = Instant.now()
        val event = ChatUserRemovedEvent(1L, state.id, message.requester, timestamp, message.userToRemove)

        val task = RemoveUserEventProcessor.createSuccessReply(message, event, state)
        task.reply.replyTo shouldBe message.replyTo
        task.reply.response shouldBe RemoveUserFromChatResponse(Right(Ok()))
        task.broadcast shouldBe Some(ChatClientActor.UserRemovedFromChat(
          event.id, event.eventNumber, event.timestamp, event.user, event.userRemoved))
      }
    }

    "creating an error reply" must {
      "compute the correct reply" in {
        val reply = RemoveUserEventProcessor.createErrorReply(UnknownError())
        reply shouldBe RemoveUserFromChatResponse(Left(UnknownError()))
      }
    }
  }

  private def createMessage(requester: DomainUserId, userToRemove: DomainUserId): RemoveUserFromChatRequest = {
    val replyTo = TestProbe[RemoveUserFromChatResponse]()
    RemoveUserFromChatRequest(domainId, chatId, requester, userToRemove, replyTo.ref)
  }
}


