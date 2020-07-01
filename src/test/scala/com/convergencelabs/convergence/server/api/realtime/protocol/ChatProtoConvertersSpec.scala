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

package com.convergencelabs.convergence.server.api.realtime.protocol

import java.time.Instant

import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.proto.chat._
import com.convergencelabs.convergence.proto.core.{DomainUserIdData, DomainUserTypeData, PermissionsList, UserPermissionsEntry}
import com.convergencelabs.convergence.server.api.realtime.protocol.ChatProtoConverters._
import com.convergencelabs.convergence.server.backend.datastore.domain.chat._
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{GroupPermissions, UserPermissions}
import com.convergencelabs.convergence.server.model.domain.chat
import com.convergencelabs.convergence.server.model.domain.chat.{ChatCreatedEvent, ChatMember, ChatMembership, ChatMessageEvent, ChatNameChangedEvent, ChatState, ChatTopicChangedEvent, ChatType, ChatUserAddedEvent, ChatUserJoinedEvent, ChatUserLeftEvent, ChatUserRemovedEvent}
import com.google.protobuf.timestamp.Timestamp
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ChatProtoConvertersSpec extends AnyWordSpec with Matchers {
  private[this] val chatId = "chatId"
  private[this] val created = Instant.now()
  private[this] val name = "name"
  private[this] val topic = "topic"
  private[this] val lastEventNumber = 0
  private[this] val lastEventTime = Instant.now()

  private[this] val userId1 = DomainUserId.normal("user1")
  private[this] val userId2 = DomainUserId.convergence("user2")
  private[this] val userId3 = DomainUserId.anonymous("user3")

  private[this] val member1 = ChatMember(chatId, userId1, 0L)
  private[this] val member2 = chat.ChatMember(chatId, userId2, 10L)
  private[this] val member3 = chat.ChatMember(chatId, userId3, 20L)

  private[this] val protoUserId1 = DomainUserIdData(DomainUserTypeData.Normal, userId1.username)
  private[this] val protoUserId2 = DomainUserIdData(DomainUserTypeData.Convergence, userId2.username)
  private[this] val protoUserId3 = DomainUserIdData(DomainUserTypeData.Anonymous, userId3.username)

  private[this] val protoMember1 = ChatMemberData(Some(protoUserId1), member1.maxSeenEvent)
  private[this] val protoMember2 = ChatMemberData(Some(protoUserId2), member2.maxSeenEvent)
  private[this] val protoMember3 = ChatMemberData(Some(protoUserId3), member3.maxSeenEvent)

  "An ChatProtoConverters" when {
    "converting a ChatInfo to protocol buffers" must {
      "correctly convert the chat state" in {
        val state = ChatState(chatId,
          ChatType.Channel,
          created,
          ChatMembership.Public,
          name,
          topic,
          lastEventTime,
          lastEventNumber,
          Set(member1, member2, member3))

        val proto = chatStateToProto(state)

        proto.id shouldBe state.id
        proto.chatType shouldBe "channel"
        proto.createdTime shouldBe Some(Timestamp(created.getEpochSecond, created.getNano))
        proto.name shouldBe state.name
        proto.topic shouldBe state.topic
        proto.lastEventNumber shouldBe state.lastEventNumber
        proto.lastEventTime shouldBe Some(Timestamp(lastEventTime.getEpochSecond, lastEventTime.getNano))
        proto.members shouldBe Seq(protoMember1, protoMember2, protoMember3)
      }
    }

    "converting a GroupPermissions from protocol buffers" must {
      "correctly convert group permission data" in {
        val list1 = PermissionsList(Seq("1", "2"))
        val list2 = PermissionsList(Seq("2", "3"))
        val g1 = "g1"
        val g2 = "g2"

        val groupPermissionData: Map[String, PermissionsList] = Map(g1 -> list1, g2 -> list2)
        protoToGroupPermissions(groupPermissionData) shouldBe Set(
          GroupPermissions(g1, list1.values.toSet),
          GroupPermissions(g2, list2.values.toSet),
        )
      }
    }

    "converting a UserPermissions from protocol buffers" must {
      "correctly convert group permission data" in {
        val up1 = UserPermissionsEntry(Some(protoUserId1), Seq("1", "2"))
        val up2 = UserPermissionsEntry(Some(protoUserId2), Seq("2", "3"))

        val userPermissionData: Seq[UserPermissionsEntry] = Seq(up1, up2)
        protoToUserPermissions(userPermissionData) shouldBe Set(
          UserPermissions(userId1, up1.permissions.toSet),
          permissions.UserPermissions(userId2, up2.permissions.toSet)
        )
      }
    }

    "converting a ChatType to protocol buffers" must {
      "correctly convert ChatType.Channel" in {
        chatTypeToProto(ChatType.Channel) shouldBe "channel"
      }

      "correctly convert ChatType.Room" in {
        chatTypeToProto(ChatType.Room) shouldBe "room"
      }

      "correctly convert ChatType.Direct" in {
        chatTypeToProto(ChatType.Direct) shouldBe "direct"
      }
    }

    "converting a ChatMembership to protocol buffers" must {
      "correctly convert ChatMembership.Public" in {
        chatMembershipToProto(ChatMembership.Public) shouldBe "public"
      }

      "correctly convert ChatMembership.Private" in {
        chatMembershipToProto(ChatMembership.Private) shouldBe "private"
      }
    }

    "converting a ChatEvent to protocol buffers" must {
      "correctly convert a ChatCreatedEvent" in {
        val event = ChatCreatedEvent(0L, chatId, userId1, Instant.now(), name, topic, Set(userId1, userId2, userId3))
        chatEventToProto(event) shouldBe ChatEventData().withCreated(
          ChatCreatedEventData(
            event.id,
            event.eventNumber,
            Some(Timestamp(event.timestamp.getEpochSecond, event.timestamp.getNano)),
            Some(protoUserId1),
            name,
            topic,
            Seq(protoUserId1, protoUserId2, protoUserId3))
        )
      }

      "correctly convert a ChatUserJoinedEvent" in {
        val event = ChatUserJoinedEvent(0L, chatId, userId1, Instant.now())
        chatEventToProto(event) shouldBe ChatEventData().withUserJoined(
          ChatUserJoinedEventData(
            event.id,
            event.eventNumber,
            Some(Timestamp(event.timestamp.getEpochSecond, event.timestamp.getNano)),
            Some(protoUserId1)
          )
        )
      }

      "correctly convert a ChatUserLeftEvent" in {
        val event = ChatUserLeftEvent(0L, chatId, userId1, Instant.now())
        chatEventToProto(event) shouldBe ChatEventData().withUserLeft(
          ChatUserLeftEventData(
            event.id,
            event.eventNumber,
            Some(Timestamp(event.timestamp.getEpochSecond, event.timestamp.getNano)),
            Some(protoUserId1)
          )
        )
      }

      "correctly convert a ChatUserAddedEvent" in {
        val event = ChatUserAddedEvent(0L, chatId, userId1, Instant.now(), userId2)
        chatEventToProto(event) shouldBe ChatEventData().withUserAdded(
          ChatUserAddedEventData(
            event.id,
            event.eventNumber,
            Some(Timestamp(event.timestamp.getEpochSecond, event.timestamp.getNano)),
            Some(protoUserId1),
            Some(protoUserId2)
          )
        )
      }

      "correctly convert a ChatUserRemovedEvent" in {
        val event = ChatUserRemovedEvent(0L, chatId, userId1, Instant.now(), userId2)
        chatEventToProto(event) shouldBe ChatEventData().withUserRemoved(
          ChatUserRemovedEventData(
            event.id,
            event.eventNumber,
            Some(Timestamp(event.timestamp.getEpochSecond, event.timestamp.getNano)),
            Some(protoUserId1),
            Some(protoUserId2)
          )
        )
      }

      "correctly convert a ChatMessageEvent" in {
        val event = ChatMessageEvent(0L, chatId, userId1, Instant.now(), "message")
        chatEventToProto(event) shouldBe ChatEventData().withMessage(
          ChatMessageEventData(
            event.id,
            event.eventNumber,
            Some(Timestamp(event.timestamp.getEpochSecond, event.timestamp.getNano)),
            Some(protoUserId1),
            event.message
          )
        )
      }

      "correctly convert a ChatNameChangedEvent" in {
        val event = ChatNameChangedEvent(0L, chatId, userId1, Instant.now(), "name")
        chatEventToProto(event) shouldBe ChatEventData().withNameChanged(
          ChatNameChangedEventData(
            event.id,
            event.eventNumber,
            Some(Timestamp(event.timestamp.getEpochSecond, event.timestamp.getNano)),
            Some(protoUserId1),
            event.name
          )
        )
      }

      "correctly convert a ChatTopicChangedEvent" in {
        val event = ChatTopicChangedEvent(0L, chatId, userId1, Instant.now(), "topic")
        chatEventToProto(event) shouldBe ChatEventData().withTopicChanged(
          ChatTopicChangedEventData(
            event.id,
            event.eventNumber,
            Some(Timestamp(event.timestamp.getEpochSecond, event.timestamp.getNano)),
            Some(protoUserId1),
            event.topic
          )
        )
      }
    }
  }
}
