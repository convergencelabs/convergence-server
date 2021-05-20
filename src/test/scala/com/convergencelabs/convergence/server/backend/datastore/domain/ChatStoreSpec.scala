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

package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.model.domain.chat._
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.Instant

class ChatStoreSpec
  extends DomainPersistenceStoreSpec
  with AnyWordSpecLike
  with Matchers {

  private val user1 = "user1"
  private val user1Id = DomainUserId(DomainUserType.Normal, user1)

  private val user2 = "user2"
  private val user2Id = DomainUserId(DomainUserType.Normal, user2)

  private val user3 = "user3"
  private val user3Id = DomainUserId(DomainUserType.Normal, user3)

  private val channel1Id = "channel1"
  private val firstId = "direct:1"

  "A ChatChannelStore" when {
    "creating a chat channel" must {
      "return the id if provided" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Direct, Instant.now(), ChatMembership.Public, "", "", Some(Set(user1Id, user1Id)), user1Id).get
        id shouldEqual channel1Id
      }

      "return a generated id if none is provided" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          None, ChatType.Direct, Instant.now(), ChatMembership.Public, "", "", None, user1Id).get
        id shouldEqual firstId
      }

      "throw exception if id is duplicate" in withTestData { provider =>
        provider.chatStore.createChat(
          Some(channel1Id), ChatType.Direct, Instant.now(), ChatMembership.Public, "", "", None, user1Id).get
        an[DuplicateValueException] should be thrownBy provider.chatStore.createChat(
          Some(channel1Id), ChatType.Direct, Instant.now(), ChatMembership.Public, "", "", None, user1Id).get
      }

      "not create channel if members are invalid" in withTestData { provider =>
        an[EntityNotFoundException] should be thrownBy {
          provider.chatStore.createChat(
            Some(channel1Id),
            ChatType.Direct,
            Instant.now(),
            ChatMembership.Public,
            "",
            "",
            Some(Set(user1Id, DomainUserId(DomainUserType.Normal, "does_not_exist"))),
            user1Id).get
        }
      }
    }

    "getting a chat chat state" must {
      "return chat state for valid id" in withTestData { provider =>
        val name = "testName"
        val topic = "testTopic"
        val members = Set(user1Id, user2Id)
        val timestamp = Instant.now()

        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Direct, timestamp, ChatMembership.Public, name, topic, Some(members), user1Id).get

        val chatChannelInfo = provider.chatStore.getChatState(id).get
        chatChannelInfo.id shouldEqual id
        chatChannelInfo.name shouldEqual "testName"
        chatChannelInfo.topic shouldEqual "testTopic"
        chatChannelInfo.chatType shouldEqual ChatType.Direct
        chatChannelInfo.lastEventNumber shouldEqual 0L
        chatChannelInfo.lastEventTime.toEpochMilli shouldEqual timestamp.toEpochMilli
        chatChannelInfo.members shouldEqual Map(
          user1Id -> ChatMember(channel1Id, user1Id, 0),
          user2Id -> ChatMember(channel1Id, user2Id, 0))
      }

      "return the correct max event no" in withTestData { provider =>
        val name = "testName"
        val topic = "testTopic"
        val members = Set(user1Id)
        val timestamp = Instant.now()

        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Channel, timestamp, ChatMembership.Public, name, topic, Some(members), user1Id).get

        provider.chatStore.addChatMessageEvent(ChatMessageEvent(1, id, user1Id, timestamp, "foo"))
        val chatChannelInfo = provider.chatStore.getChatState(id).get

        chatChannelInfo.id shouldEqual id
        chatChannelInfo.name shouldEqual "testName"
        chatChannelInfo.topic shouldEqual "testTopic"
        chatChannelInfo.chatType shouldEqual ChatType.Channel
        chatChannelInfo.lastEventNumber shouldEqual 1L
        chatChannelInfo.lastEventTime.toEpochMilli shouldEqual timestamp.toEpochMilli
        chatChannelInfo.members shouldEqual Map(user1Id -> ChatMember(channel1Id, user1Id, 0))
      }

      "throw error for invalid id" in withTestData { provider =>
        an[EntityNotFoundException] should be thrownBy provider.chatStore.getChatState("does_not_exist").get
      }
    }

    "getting chat channel members" must {
      "return the correct users after a create" in withTestData { provider =>
        provider.chatStore.createChat(
          Some(channel1Id), ChatType.Direct, Instant.now(), ChatMembership.Public, "testName", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        val members = provider.chatStore.getChatMembers(channel1Id).get
        members shouldEqual Set(user1Id, user2Id)
      }
    }

    "removing a user from all chats" must {
      "correctly remove the right user from all chats" in withTestData { provider =>
        val chatId = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Channel, Instant.now(), ChatMembership.Public, "test1", "testTopic",
          Some(Set(user1Id, user2Id)), user2Id).get

        provider.chatStore.removeUserFromAllChats(user1Id).get

        val members = provider.chatStore.getChatMembers(channel1Id).get
        members shouldEqual Set(user2Id)

        val chat = provider.chatStore.getChatState(chatId).get
        chat.members shouldEqual Map(user2Id -> ChatMember(chatId, user2Id, 0L))
      }
    }

    "getting a direct chat channel by its members" must {
      "return the correct chat channel" in withTestData { provider =>
        val members = Set(user1Id, user2Id)
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Direct, Instant.now(), ChatMembership.Private, "testName", "testTopic", Some(members), user1Id).get
        val info = provider.chatStore.getDirectChatInfoByUsers(members).get.value
        info.id shouldBe id
      }
    }

    "getting joined channels" must {
      "return the correct chat channels" in withTestData { provider =>
        val id1 = provider.chatStore.createChat(
          Some("c1"), ChatType.Channel, Instant.now(), ChatMembership.Public, "test1", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        val id2 = provider.chatStore.createChat(
          Some("c2"), ChatType.Channel, Instant.now(), ChatMembership.Public, "test2", "testTopic", Some(Set(user1Id, user3Id)), user1Id).get
        provider.chatStore.createChat(
          Some("c3"), ChatType.Channel, Instant.now(), ChatMembership.Public, "test3", "testTopic", Some(Set(user2Id, user3Id)), user1Id).get

        val joined = provider.chatStore.getJoinedChannels(user1Id).get

        joined.map(i => i.id) shouldBe Set(id1, id2)
      }
    }

    "creating chat channel events" must {
      "successfully create a message event" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Channel, Instant.now(), ChatMembership.Public, "testName", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        provider.chatStore.addChatMessageEvent(ChatMessageEvent(1, id, user2Id, Instant.now(), "some message")).get
        val events = provider.chatStore.getChatEvents(id, None, None, QueryOffset(), QueryLimit(), None, None).get
        events.data.size shouldEqual 2
      }

      "successfully create a name change event" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Channel, Instant.now(), ChatMembership.Public, "testName", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        provider.chatStore.addChatNameChangedEvent(ChatNameChangedEvent(2, id, user2Id, Instant.now(), "new name")).get
        val events = provider.chatStore.getChatEvents(id, None, None, QueryOffset(), QueryLimit(), None, None).get
        events.data.size shouldEqual 2
      }

      "successfully create a topic change event" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Channel, Instant.now(), ChatMembership.Public, "testName", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        provider.chatStore.addChatTopicChangedEvent(ChatTopicChangedEvent(3, id, user2Id, Instant.now(), "new topic")).get
        val events = provider.chatStore.getChatEvents(id, None, None, QueryOffset(), QueryLimit(), None, None).get
        events.data.size shouldEqual 2
      }

      "successfully create a leave event" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Channel, Instant.now(), ChatMembership.Public, "testName", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        provider.chatStore.addChatUserLeftEvent(ChatUserLeftEvent(4, id, user2Id, Instant.now())).get
        val events = provider.chatStore.getChatEvents(id, None, None, QueryOffset(), QueryLimit(), None, None).get
        events.data.size shouldEqual 2
      }

      "successfully create a joined event" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Channel, Instant.now(), ChatMembership.Public, "testName", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        provider.chatStore.addChatUserJoinedEvent(ChatUserJoinedEvent(5, id, user3Id, Instant.now())).get
        val events = provider.chatStore.getChatEvents(id, None, None, QueryOffset(), QueryLimit(), None, None).get
        events.data.size shouldEqual 2
      }

      "successfully create a user removed event" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Channel, Instant.now(), ChatMembership.Public, "testName", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        provider.chatStore.addChatUserRemovedEvent(ChatUserRemovedEvent(6, id, user2Id, Instant.now(), user1Id)).get
        val events = provider.chatStore.getChatEvents(id, None, None, QueryOffset(), QueryLimit(), None, None).get
        events.data.size shouldEqual 2
      }

      "successfully create an add user event" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Channel, Instant.now(), ChatMembership.Public, "testName", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        provider.chatStore.addChatUserAddedEvent(ChatUserAddedEvent(7, id, user1Id, Instant.now(), user3Id)).get
        val events = provider.chatStore.getChatEvents(id, None, None, QueryOffset(), QueryLimit(), None, None).get
        events.data.size shouldEqual 2
      }
    }
  }

  def withTestData(testCode: DomainPersistenceProvider => Any): Unit = {
    this.withPersistenceStore { provider =>
      provider.userStore.createDomainUser(DomainUser(user1Id, None, None, None, None, None)).get
      provider.userStore.createDomainUser(DomainUser(user2Id, None, None, None, None, None)).get
      provider.userStore.createDomainUser(DomainUser(user3Id, None, None, None, None, None)).get
      testCode(provider)
    }
  }
}
