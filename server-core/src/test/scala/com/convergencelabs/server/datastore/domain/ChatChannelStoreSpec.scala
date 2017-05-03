package com.convergencelabs.server.datastore.domain

import org.scalatest.Matchers
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.domain.ChatChannelStore.ChannelType
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.convergencelabs.server.datastore.EntityNotFoundException
import java.time.Instant
import com.convergencelabs.server.datastore.DuplicateValueException

class ChatChannelStoreSpec
    extends PersistenceStoreSpec[DomainPersistenceProvider](DeltaCategory.Domain)
    with WordSpecLike
    with Matchers {

  val user1 = "user1"
  val user2 = "user2"
  val user3 = "user3"

  val channel1Id = "channel1"
  val firstId = "#1"

  def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider = new DomainPersistenceProvider(dbProvider)

  "A ChatChannelStore" when {
    "creating a chat channel" must {
      "return the id if provided" in withTestData { provider =>
        val id = provider.chatChannelStore.createChatChannel(Some(channel1Id), ChannelType.Direct, false, "", "", Some(Set(user1, user2))).get
        id shouldEqual channel1Id
      }

      "return a generated id if none is provided" in withTestData { provider =>
        val id = provider.chatChannelStore.createChatChannel(None, ChannelType.Direct, false, "", "", None).get
        id shouldEqual firstId
      }

      "throw exception if id is duplicate" in withTestData { provider =>
        provider.chatChannelStore.createChatChannel(Some(channel1Id), ChannelType.Direct, false, "", "", None).get
        an[DuplicateValueException] should be thrownBy provider.chatChannelStore.createChatChannel(
          Some(channel1Id), ChannelType.Direct, false, "", "", None).get
      }

      "not create channel if members are invalid" in withTestData { provider =>
        provider.chatChannelStore.createChatChannel(
          Some(channel1Id), ChannelType.Direct, false, "", "", Some(Set(user1, "does_not_exist"))).get
        an[EntityNotFoundException] should be thrownBy provider.chatChannelStore.getChatChannel("does_not_exist").get
      }
    }

    "getting a chat channel" must {
      "return chat channel for valid id" in withTestData { provider =>
        val id = provider.chatChannelStore.createChatChannel(Some(channel1Id), ChannelType.Direct, false, "testName", "testTopic", Some(Set(user1, user2))).get
        val chatChannel = provider.chatChannelStore.getChatChannel(id).get
        chatChannel.id shouldEqual id
        chatChannel.name shouldEqual "testName"
        chatChannel.topic shouldEqual "testTopic"
        chatChannel.channelType shouldEqual "direct"
      }

      "throw error for invalid id" in withTestData { provider =>
        an[EntityNotFoundException] should be thrownBy provider.chatChannelStore.getChatChannel("does_not_exist").get
      }
    }

    "getting a chat channel info" must {
      "return chat channel for valid id" in withTestData { provider =>
        val name = "testName"
        val topic = "testTopic"
        val members = Set(user1, user2)
        val timestamp = Instant.now()

        val id = provider.chatChannelStore.createChatChannel(Some(channel1Id), ChannelType.Direct, false, name, topic, Some(members)).get
        provider.chatChannelStore.addChatCreatedEvent(ChatCreatedEvent(0, id, user1, timestamp, name, topic, members)).get

        val chatChannelInfo = provider.chatChannelStore.getChatChannelInfo(id).get
        chatChannelInfo.id shouldEqual id
        chatChannelInfo.name shouldEqual "testName"
        chatChannelInfo.topic shouldEqual "testTopic"
        chatChannelInfo.channelType shouldEqual "direct"
        chatChannelInfo.lastEventNo shouldEqual 0L
        chatChannelInfo.lastEventTime shouldEqual timestamp
        chatChannelInfo.members shouldEqual members
      }

      "throw error for invalid id" in withTestData { provider =>
        an[EntityNotFoundException] should be thrownBy provider.chatChannelStore.getChatChannelInfo("does_not_exist").get
      }
    }

    "getting chat channel members" must {
      "return the correct users after a create" in withTestData { provider =>
        val id = provider.chatChannelStore.createChatChannel(Some(channel1Id), ChannelType.Direct, false, "testName", "testTopic", Some(Set(user1, user2))).get
        val members = provider.chatChannelStore.getChatChannelMembers(channel1Id).get
        members shouldEqual Set(user1, user2)
      }
    }

    "creating chat channel events" must {
      "successfully create all chat events" in withTestData { provider =>
        val id = provider.chatChannelStore.createChatChannel(Some(channel1Id), ChannelType.Direct, false, "testName", "testTopic", Some(Set(user1, user2))).get
        provider.chatChannelStore.addChatCreatedEvent(ChatCreatedEvent(0, id, user1, Instant.now(), "testName", "testTopic", Set(user1, user2))).get
        provider.chatChannelStore.addChatMessageEvent(ChatMessageEvent(1, id, user2, Instant.now(), "some message")).get
        provider.chatChannelStore.addChatNameChangedEvent(ChatNameChangedEvent(2, id, user2, Instant.now(), "new name")).get
        provider.chatChannelStore.addChatTopicChangedEvent(ChatTopicChangedEvent(3, id, user2, Instant.now(), "new topic")).get
        provider.chatChannelStore.addChatUserLeftEvent(ChatUserLeftEvent(4, id, user3, Instant.now())).get
        provider.chatChannelStore.addChatUserJoinedEvent(ChatUserJoinedEvent(5, id, user3, Instant.now())).get
        provider.chatChannelStore.addChatUserRemovedEvent(ChatUserRemovedEvent(6, id, user2, Instant.now(), user1)).get
        provider.chatChannelStore.addChatUserAddedEvent(ChatUserAddedEvent(7, id, user2, Instant.now(), user1)).get
        val events = provider.chatChannelStore.getChatChannelEvents(id, None, None, None, None).get
        events.size shouldEqual 8
      }
    }
  }

  def withTestData(testCode: DomainPersistenceProvider => Any): Unit = {
    this.withPersistenceStore { provider =>
      provider.userStore.createDomainUser(DomainUser(DomainUserType.Normal, user1, None, None, None, None)).get
      provider.userStore.createDomainUser(DomainUser(DomainUserType.Normal, user2, None, None, None, None)).get
      provider.userStore.createDomainUser(DomainUser(DomainUserType.Normal, user3, None, None, None, None)).get
      testCode(provider)
    }
  }
}
