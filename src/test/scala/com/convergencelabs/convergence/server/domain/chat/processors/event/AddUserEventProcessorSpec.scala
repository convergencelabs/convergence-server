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
import com.convergencelabs.convergence.server.datastore.domain.{ChatMember, ChatStore, ChatUserAddedEvent, PermissionsStore}
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{AddUserToChatRequest, AddUserToChatResponse}
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatPermissions}
import com.orientechnologies.orient.core.id.ORecordId
import org.mockito.{Matchers, Mockito}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.util.Success

class AddUserEventProcessorSpec extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with MockitoSugar
  with TestConstants {

  "An AddUserEventProcessor" when {

    "processing a request" must {
      "return success if the user is not already a member" in {
        val message = createMessage(nonMember)

        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatUserAddedEvent(Matchers.any())).thenReturn(Success())
        Mockito.when(chatStore.getChatRid(Matchers.any())).thenReturn(Success(ORecordId.EMPTY_RECORD_ID))

        val permissionsStore = mock[PermissionsStore]
        Mockito.when(permissionsStore.hasPermissionForRecord(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Success(true))

        Mockito.when(permissionsStore.addUserPermissions(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Success(()))

        AddUserEventProcessor.execute(message, state, chatStore, permissionsStore)
      }
    }

    "validating a request" must {
      "return success if the user is not already a member" in {
        val message = createMessage(nonMember)
        AddUserEventProcessor.validateMessage(message, state) shouldBe Right(())
      }

      "return a failure response if the user is already a member" in {
        val message = createMessage(member1.userId)
        AddUserEventProcessor.validateMessage(message, state) shouldBe
          Left(AddUserToChatResponse(Left(ChatActor.ChatAlreadyJoinedError())))
      }
    }

    "creating an event" must {
      "create a proper event" in {
        val message = createMessage(nonMember)
        val event = AddUserEventProcessor.createEvent(message, state)

        event.id shouldBe state.id
        event.user shouldBe message.requester
        event.userAdded shouldBe message.userToAdd
        event.eventNumber shouldBe state.lastEventNumber + 1
        event.timestamp.compareTo(Instant.now()) <= 0 shouldBe true
        event.timestamp.compareTo(state.lastEventTime) >= 0 shouldBe true
      }
    }

    "persisting an event" must {
      "create a proper event" in {
        val chatStore = mock[ChatStore]
        Mockito.when(chatStore.addChatUserAddedEvent(Matchers.any())).thenReturn(Success())
        Mockito.when(chatStore.getChatRid(Matchers.any())).thenReturn(Success(ORecordId.EMPTY_RECORD_ID))

        val permissionsStore = mock[PermissionsStore]
        Mockito.when(permissionsStore.addUserPermissions(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Success(()))

        val timestamp = Instant.now()
        val event = ChatUserAddedEvent(1L, state.id, member1.userId, timestamp, nonMember)

        AddUserEventProcessor.persistEvent(chatStore, permissionsStore)(event)

        Mockito.verify(chatStore).getChatRid(state.id)
        Mockito.verify(chatStore).addChatUserAddedEvent(event)
        Mockito.verify(permissionsStore).addUserPermissions(ChatPermissions.DefaultChatPermissions, event.userAdded, Some(ORecordId.EMPTY_RECORD_ID))
      }
    }

    "updating state" must {
      "compute the correct state" in {

        val timestamp = Instant.now()
        val event = ChatUserAddedEvent(1L, state.id, member1.userId, timestamp, nonMember)

        val newState = AddUserEventProcessor.updateState(event, state)
        newState shouldBe state.copy(
          members = state.members + (event.userAdded -> ChatMember(state.id, event.userAdded, 0L)),
          lastEventNumber = event.eventNumber,
          lastEventTime = timestamp
        )
      }
    }

    "creating a success reply" must {
      "compute the correct reply" in {

        val message = createMessage(member1.userId)

        val timestamp = Instant.now()
        val event = ChatUserAddedEvent(1L, state.id, message.requester, timestamp, message.userToAdd)

        val task = AddUserEventProcessor.createSuccessReply(message, event, state)
        assert(task.reply.replyTo == message.replyTo)
        assert(task.reply.response == AddUserToChatResponse(Right(())))
        task.broadcast shouldBe Some(ChatClientActor.UserAddedToChat(
          event.id, event.eventNumber, event.timestamp, event.user, event.userAdded))
      }
    }

    "creating an error reply" must {
      "compute the correct reply" in {
        val reply = AddUserEventProcessor.createErrorReply(ChatActor.UnknownError())
        reply shouldBe ChatActor.AddUserToChatResponse(Left(ChatActor.UnknownError()))
      }
    }
  }
  
  private def createMessage(userToAdd: DomainUserId): AddUserToChatRequest = {
    val replyTo = TestProbe[AddUserToChatResponse]
    AddUserToChatRequest(domainId, chatId, requester, userToAdd, replyTo.ref)
  }
}


