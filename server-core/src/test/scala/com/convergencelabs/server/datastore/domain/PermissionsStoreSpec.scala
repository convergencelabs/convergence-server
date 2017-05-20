package com.convergencelabs.server.datastore.domain

import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.datastore.domain.ChatChannelStore.ChannelType
import java.time.Instant

class PermissionsStoreSpec
    extends PersistenceStoreSpec[DomainPersistenceProvider](DeltaCategory.Domain)
    with WordSpecLike
    with Matchers {

  val channel1 = "channel1"
  val channel2 = "channel2"
  val user1 = "user1"
  val user2 = "user2"
  val user3 = "user3"

  val domainUser1 = DomainUser(DomainUserType.Normal, user1, None, None, None, None)
  val domainUser2 = DomainUser(DomainUserType.Normal, user2, None, None, None, None)
  val domainUser3 = DomainUser(DomainUserType.Normal, user3, None, None, None, None)

  val group1 = "group1"
  val group2 = "group2"
  
  val userGroup1 = UserGroup(group1, group1, Set(user1, user2))
  val userGroup2 = UserGroup(group2, group2, Set(user2, user3))

  val nonRealId = "not_real"

  val permission1 = "permission1"
  val permission2 = "permission2"
  val permission3 = "permission3"

  def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider = new DomainPersistenceProvider(dbProvider)

  "A PermissionsStore" when {
    "creating a permission" must {
      "succeed when creating global permission" in withTestData { provider =>
        provider.permissionsStore.addWorldPermissions(Set(permission1), None).get
      }

      "succeed when creating world permission for channel" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addWorldPermissions(Set(permission1), Some(channel)).get
      }

      "succeed when creating group permission" in withTestData { provider =>
        provider.permissionsStore.addGroupPermissions(Set(permission1), group1, None).get
      }

      "succeed when creating group permission for channel" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addGroupPermissions(Set(permission1), group1, Some(channel)).get
      }

      "succeed when creating user permission" in withTestData { provider =>
        provider.permissionsStore.addUserPermissions(Set(permission1), user1, None).get
      }

      "succeed when creating user permission for channel" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addUserPermissions(Set(permission1), user1, Some(channel)).get
      }
    }

    "asking if user has permission" must {
      "return false when permission is not set" in withTestData { provider =>
        provider.permissionsStore.addWorldPermissions(Set(permission2), None).get
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        val hasPermission = provider.permissionsStore.hasPermission(user1, channel, permission1).get
        hasPermission shouldBe false
      }

      "return true when global permission is set" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addWorldPermissions(Set(permission1), None).get
        val hasPermission = provider.permissionsStore.hasPermission(user1, channel, permission1).get
        hasPermission shouldBe true
      }

      "return true when world permission for channel is set" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addWorldPermissions(Set(permission1), Some(channel)).get
        val hasPermission = provider.permissionsStore.hasPermission(user1, channel, permission1).get
        hasPermission shouldBe true
      }

      "return true when group permission is set" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addGroupPermissions(Set(permission1), group1, None).get
        val hasPermission = provider.permissionsStore.hasPermission(user1, channel, permission1).get
        hasPermission shouldBe true
      }

      "return true when group permission for channel is set" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addGroupPermissions(Set(permission1), group1, Some(channel)).get
        val hasPermission = provider.permissionsStore.hasPermission(user1, channel, permission1).get
        hasPermission shouldBe true
      }

      "return true when user permission is set" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addUserPermissions(Set(permission1), user1, None).get
        val hasPermission = provider.permissionsStore.hasPermission(user1, channel, permission1).get
        hasPermission shouldBe true
      }

      "return true when user permission for channel is set" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addUserPermissions(Set(permission1), user1, Some(channel)).get
        val hasPermission = provider.permissionsStore.hasPermission(user1, channel, permission1).get
        hasPermission shouldBe true
      }
    }
    "retrieving permissions" must {
      "return correct global permissions" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addWorldPermissions(Set(permission1, permission2), None).get
        val globalPermissions = provider.permissionsStore.getWorldPermissions(None).get
        globalPermissions shouldBe Set(WorldPermission(permission1), WorldPermission(permission2))
      }
      "return correct world permissions for channel" in withTestData { provider =>
        val channelRid = provider.chatChannelStore.getChatChannelRid(channel1).get
        val channel2Rid = provider.chatChannelStore.getChatChannelRid(channel2).get
        provider.permissionsStore.addWorldPermissions(Set(permission1, permission2), Some(channelRid)).get
        provider.permissionsStore.addWorldPermissions(Set(permission3), Some(channel2Rid)).get
        val worldPermissions = provider.permissionsStore.getWorldPermissions(Some(channelRid)).get
        worldPermissions shouldBe Set(WorldPermission(permission1), WorldPermission(permission2))
      }

      "return correct user permissions" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addUserPermissions(Set(permission1, permission2), user1, None).get
        provider.permissionsStore.addUserPermissions(Set(permission3), user2, None).get
        val globalPermissions = provider.permissionsStore.getAllUserPermissions(None).get
        globalPermissions shouldBe Set(UserPermission(domainUser1, permission1),
          UserPermission(domainUser1, permission2),
          UserPermission(domainUser2, permission3))
      }
      "return correct user permissions for channel" in withTestData { provider =>
        val channelRid = provider.chatChannelStore.getChatChannelRid(channel1).get
        val channel2Rid = provider.chatChannelStore.getChatChannelRid(channel2).get
        provider.permissionsStore.addUserPermissions(Set(permission1, permission2), user1, Some(channelRid)).get
        provider.permissionsStore.addUserPermissions(Set(permission3), user2, Some(channel2Rid)).get
        val worldPermissions = provider.permissionsStore.getAllUserPermissions(Some(channelRid)).get
        worldPermissions shouldBe Set(UserPermission(domainUser1, permission1), UserPermission(domainUser1, permission2))
      }
      
      "return correct group permissions" in withTestData { provider =>
        val channel = provider.chatChannelStore.getChatChannelRid(channel1).get
        provider.permissionsStore.addGroupPermissions(Set(permission1, permission2), group1, None).get
        provider.permissionsStore.addGroupPermissions(Set(permission3), group2, None).get
        val globalPermissions = provider.permissionsStore.getAllGroupPermissions(None).get
        globalPermissions shouldBe Set(GroupPermission(userGroup1, permission1),
          GroupPermission(userGroup1, permission2),
          GroupPermission(userGroup2, permission3))
      }
      "return correct group permissions for channel" in withTestData { provider =>
        val channelRid = provider.chatChannelStore.getChatChannelRid(channel1).get
        val channel2Rid = provider.chatChannelStore.getChatChannelRid(channel2).get
        provider.permissionsStore.addGroupPermissions(Set(permission1, permission2), group1, Some(channelRid)).get
        provider.permissionsStore.addGroupPermissions(Set(permission3), group2, Some(channel2Rid)).get
        val worldPermissions = provider.permissionsStore.getAllGroupPermissions(Some(channelRid)).get
        worldPermissions shouldBe Set(GroupPermission(userGroup1, permission1), GroupPermission(userGroup1, permission2))
      }
    }
  }

  def withTestData(testCode: DomainPersistenceProvider => Any): Unit = {
    this.withPersistenceStore { provider =>
      provider.userStore.createDomainUser(domainUser1).get
      provider.userStore.createDomainUser(domainUser2).get
      provider.userStore.createDomainUser(domainUser3).get

      provider.userGroupStore.createUserGroup(userGroup1).get
      provider.userGroupStore.createUserGroup(userGroup2).get

      provider.chatChannelStore.createChatChannel(
          Some(channel1), ChannelType.Group, Instant.now(), false, "name", "topic", Some(Set(user1, user2, user3)), user1).get
      provider.chatChannelStore.createChatChannel(
          Some(channel2), ChannelType.Group, Instant.now(), false, "name", "topic", Some(Set(user1, user2, user3)), user1).get

      testCode(provider)
    }
  }
}
