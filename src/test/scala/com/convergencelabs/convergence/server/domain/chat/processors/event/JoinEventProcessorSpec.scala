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
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatPermissions}
import com.convergencelabs.convergence.server.domain.chat.ChatActor._
import com.orientechnologies.orient.core.id.ORecordId
import org.mockito.{Matchers, Mockito}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Failure, Success}

class JoinEventProcessorSpec extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with MockitoSugar
  with TestConstants {

  "An JoinEventProcessor" when {

    "processing a request" must {
      "return success if the user is not a member" in {
        val message = createMessage(nonMember)

        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatUserJoinedEvent(Matchers.any())).thenReturn(Success(()))
        Mockito.when(chatStore.getChatRid(Matchers.any())).thenReturn(Success(ORecordId.EMPTY_RECORD_ID))

        val permissionsStore = mock[PermissionsStore]
        Mockito.when(permissionsStore.hasPermissionForRecord(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Success(true))

        Mockito.when(permissionsStore.addUserPermissions(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Success(()))

        Mockito.when(permissionsStore.addUserPermissions(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Success(()))

        JoinEventProcessor.execute(message, state, chatStore, permissionsStore)
      }
    }

    "validating a request" must {
      "return success if the user is not a member" in {
        val message = createMessage(nonMember)
        JoinEventProcessor.validateMessage(message, state) shouldBe Right(())
      }

      "return a failure response if the user is a member" in {
        val message = createMessage(member1.userId)
        JoinEventProcessor.validateMessage(message, state) shouldBe
          Left(JoinChatResponse(Left(ChatActor.ChatAlreadyJoinedError())))
      }
    }

    "creating an event" must {
      "create a proper event" in {
        val message = createMessage(nonMember)
        val event = JoinEventProcessor.createEvent(message, state)

        event.id shouldBe state.id
        event.user shouldBe message.requester
        event.eventNumber shouldBe state.lastEventNumber + 1
        event.timestamp.compareTo(Instant.now()) <= 0 shouldBe true
        event.timestamp.compareTo(state.lastEventTime) >= 0 shouldBe true
      }
    }

    "persisting an event" must {
      "succeed when the persistence operations succeed" in {
        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatUserJoinedEvent(Matchers.any())).thenReturn(Success(()))
        Mockito.when(chatStore.getChatRid(Matchers.any())).thenReturn(Success(ORecordId.EMPTY_RECORD_ID))

        val permissionsStore = mock[PermissionsStore]
        Mockito.when(permissionsStore.addUserPermissions(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Success(()))

        val timestamp = Instant.now()
        val event = ChatUserJoinedEvent(1L, state.id, nonMember, timestamp)

        val result = JoinEventProcessor.persistEvent(chatStore, permissionsStore)(event)
        result shouldBe Success(())

        Mockito.verify(chatStore).addChatUserJoinedEvent(event)
        Mockito.verify(chatStore).getChatRid(state.id)
        Mockito.verify(permissionsStore).addUserPermissions(ChatPermissions.DefaultChatPermissions, event.user, Some(ORecordId.EMPTY_RECORD_ID))
      }

      "fail when the persistence operations fail" in {
        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatUserJoinedEvent(Matchers.any())).thenReturn(Failure(InducedTestingException()))

        val permissionsStore = mock[PermissionsStore]

        val timestamp = Instant.now()
        val event = ChatUserJoinedEvent(1L, state.id, nonMember, timestamp)

        val result = JoinEventProcessor.persistEvent(chatStore, permissionsStore)(event)
        result shouldBe Failure(InducedTestingException())
      }
    }

    "updating state" must {
      "compute the correct state" in {
        val timestamp = Instant.now()
        val event = ChatUserJoinedEvent(1L, state.id, nonMember, timestamp)

        val newState = JoinEventProcessor.updateState(event, state)
        newState shouldBe state.copy(
          members = state.members + (event.user -> ChatMember(state.id, event.user, 0L)),
          lastEventNumber = event.eventNumber,
          lastEventTime = timestamp
        )
      }
    }

    "creating a success reply" must {
      "compute the correct reply" in {
        val message = createMessage(nonMember)

        val timestamp = Instant.now()
        val event = ChatUserJoinedEvent(1L, state.id, message.requester, timestamp)

        val task = JoinEventProcessor.createSuccessReply(message, event, state)
        task.reply.replyTo shouldBe message.replyTo

        val info = JoinEventProcessor.stateToInfo(state)
        task.reply.response shouldBe JoinChatResponse(Right(info))
        task.broadcast shouldBe Some(ChatClientActor.UserJoinedChat(
          event.id, event.eventNumber, event.timestamp, event.user))
      }
    }

    "creating an error reply" must {
      "compute the correct reply" in {
        val reply = JoinEventProcessor.createErrorReply(UnknownError())
        reply shouldBe JoinChatResponse(Left(UnknownError()))
      }
    }

    "creating chat info" must {
      "compute the correct info for the state" in {
        val info = JoinEventProcessor.stateToInfo(state)
        info shouldBe ChatInfo(
          state.id,
          state.chatType,
          state.created,
          state.membership,
          state.name,
          state.topic,
          state.lastEventNumber,
          state.lastEventTime,
          state.members.values.toSet)
      }
    }
  }

  private def createMessage(requester: DomainUserId): JoinChatRequest = {
    val replyTo = TestProbe[JoinChatResponse]
    val client = TestProbe[ChatClientActor.OutgoingMessage]
    JoinChatRequest(domainId, chatId, requester, client.ref, replyTo.ref)
  }
}


