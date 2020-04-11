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

package com.convergencelabs.convergence.server.datastore.domain

import java.time.Instant

import com.convergencelabs.convergence.server.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.db.schema.DeltaCategory
import com.convergencelabs.convergence.server.domain.{DomainUser, DomainUserId, DomainUserType}
import org.scalatest.OptionValues._
import org.scalatest.{Matchers, WordSpecLike}

class ChatStoreSpec
  extends PersistenceStoreSpec[DomainPersistenceProvider](DeltaCategory.Domain)
  with WordSpecLike
  with Matchers {

  private[this] val user1 = "user1"
  private[this] val user1Id = DomainUserId(DomainUserType.Normal, user1)

  private[this] val user2 = "user2"
  private[this] val user2Id = DomainUserId(DomainUserType.Normal, user2)

  private[this] val user3 = "user3"
  private[this] val user3Id = DomainUserId(DomainUserType.Normal, user3)

  private[this] val channel1Id = "channel1"
  private[this] val firstId = "#1"

  def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider = new DomainPersistenceProviderImpl(dbProvider)

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

        an[EntityNotFoundException] should be thrownBy { provider.chatStore.getChat("does_not_exist").get }
      }
    }

    "getting a chat channel" must {
      "return chat channel for valid id" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Direct, Instant.now(), ChatMembership.Public, "testName", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        val chatChannel = provider.chatStore.getChat(id).get
        chatChannel.id shouldEqual id
        chatChannel.name shouldEqual "testName"
        chatChannel.topic shouldEqual "testTopic"
        chatChannel.chatType shouldEqual ChatType.Direct
      }

      "throw error for invalid id" in withTestData { provider =>
        an[EntityNotFoundException] should be thrownBy provider.chatStore.getChat("does_not_exist").get
      }
    }

    "getting a chat channel info" must {
      "return chat channel for valid id" in withTestData { provider =>
        val name = "testName"
        val topic = "testTopic"
        val members = Set(user1Id, user2Id)
        val timestamp = Instant.now()

        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Direct, timestamp, ChatMembership.Public, name, topic, Some(members), user1Id).get

        val chatChannelInfo = provider.chatStore.getChatInfo(id).get
        chatChannelInfo.id shouldEqual id
        chatChannelInfo.name shouldEqual "testName"
        chatChannelInfo.topic shouldEqual "testTopic"
        chatChannelInfo.chatType shouldEqual ChatType.Direct
        chatChannelInfo.lastEventNumber shouldEqual 0L
        chatChannelInfo.lastEventTime.toEpochMilli shouldEqual timestamp.toEpochMilli
        chatChannelInfo.members shouldEqual Set(
          ChatMember(channel1Id, user1Id, 0),
          ChatMember(channel1Id, user2Id, 0))
      }

      "return the correct max event no" in withTestData { provider =>
        val name = "testName"
        val topic = "testTopic"
        val members = Set(user1Id)
        val timestamp = Instant.now()

        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Channel, timestamp, ChatMembership.Public, name, topic, Some(members), user1Id).get

        provider.chatStore.addChatMessageEvent(ChatMessageEvent(1, id, user1Id, timestamp, "foo"))
        val chatChannelInfo = provider.chatStore.getChatInfo(id).get

        chatChannelInfo.id shouldEqual id
        chatChannelInfo.name shouldEqual "testName"
        chatChannelInfo.topic shouldEqual "testTopic"
        chatChannelInfo.chatType shouldEqual ChatType.Channel
        chatChannelInfo.lastEventNumber shouldEqual 1L
        chatChannelInfo.lastEventTime.toEpochMilli shouldEqual timestamp.toEpochMilli
        chatChannelInfo.members shouldEqual Set(ChatMember(channel1Id, user1Id, 0))
      }

      "throw error for invalid id" in withTestData { provider =>
        an[EntityNotFoundException] should be thrownBy provider.chatStore.getChatInfo("does_not_exist").get
      }
    }

    "getting chat channel members" must {
      "return the correct users after a create" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Direct, Instant.now(), ChatMembership.Public, "testName", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        val members = provider.chatStore.getChatMembers(channel1Id).get
        members shouldEqual Set(user1Id, user2Id)
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
          None, ChatType.Channel, Instant.now(), ChatMembership.Public, "test1", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        val id2 = provider.chatStore.createChat(
          None, ChatType.Channel, Instant.now(), ChatMembership.Public, "test2", "testTopic", Some(Set(user1Id, user3Id)), user1Id).get
        val id3 = provider.chatStore.createChat(
          None, ChatType.Channel, Instant.now(), ChatMembership.Public, "test3", "testTopic", Some(Set(user2Id, user3Id)), user1Id).get

        val joined = provider.chatStore.getJoinedChats(user1Id).get

        joined.map(i => i.id).toSet shouldBe Set(id1, id2)
      }
    }

    "creating chat channel events" must {
      "successfully create all chat events" in withTestData { provider =>
        val id = provider.chatStore.createChat(
          Some(channel1Id), ChatType.Direct, Instant.now(), ChatMembership.Public, "testName", "testTopic", Some(Set(user1Id, user2Id)), user1Id).get
        provider.chatStore.addChatMessageEvent(ChatMessageEvent(1, id, user2Id, Instant.now(), "some message")).get
        provider.chatStore.addChatNameChangedEvent(ChatNameChangedEvent(2, id, user2Id, Instant.now(), "new name")).get
        provider.chatStore.addChatTopicChangedEvent(ChatTopicChangedEvent(3, id, user2Id, Instant.now(), "new topic")).get
        provider.chatStore.addChatUserLeftEvent(ChatUserLeftEvent(4, id, user3Id, Instant.now())).get
        provider.chatStore.addChatUserJoinedEvent(ChatUserJoinedEvent(5, id, user3Id, Instant.now())).get
        provider.chatStore.addChatUserRemovedEvent(ChatUserRemovedEvent(6, id, user2Id, Instant.now(), user1Id)).get
        provider.chatStore.addChatUserAddedEvent(ChatUserAddedEvent(7, id, user2Id, Instant.now(), user1Id)).get
        val events = provider.chatStore.getChatEvents(id, None, None, None, None, None, None).get
        events.data.size shouldEqual 8
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
