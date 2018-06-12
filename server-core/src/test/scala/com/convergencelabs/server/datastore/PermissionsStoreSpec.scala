package com.convergencelabs.server.datastore

import java.time.Duration

import org.scalatest.Matchers
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.UserStore.User
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.convergnece.DomainStore

case class PermissionStoreSpecStores(permissionStore: PermissionsStore, userStore: UserStore, domainStore: DomainStore)

class PermissionsStoreSpec extends PersistenceStoreSpec[PermissionStoreSpecStores](DeltaCategory.Convergence)
    with WordSpecLike with Matchers {
  def createStore(dbProvider: DatabaseProvider): PermissionStoreSpecStores = {
    PermissionStoreSpecStores(new PermissionsStore(dbProvider), new UserStore(dbProvider, Duration.ofSeconds(5)), new DomainStore(dbProvider))
  }

  val TestUser = User("username1", "test@convergence.com", "username1", "username1", "displayName")
  val TestUser2 = User("username2", "test2@convergence.com", "username2", "username2", "displayName2")
  val TestDomainFQN = DomainFqn("namespace1", "domain1")

  val TestPermission1 = Permission("testId1", "test1", "test1 description")
  val TestPermission2 = Permission("testId2", "test2", "test2 description")

  "A PermissionsStore" when {
    "saving a permission" must {
      "return success" in withPersistenceStore { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.createPermission(TestPermission1).get
      }
      "return failure it if already exists" in withPersistenceStore { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission1).isFailure shouldBe true
      }
    }

    "calling hasBeenSetup()" must {
      "return true when permissions exist" in withPersistenceStore { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.createPermission(TestPermission1).get

        permissionStore.hasBeenSetup().get shouldEqual true
      }
      "return false when no permissions exist" in withPersistenceStore { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.hasBeenSetup().get shouldEqual false
      }
    }

    "saving a role" must {
      "return success" in withPersistenceStore { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role("role1", List(TestPermission1.id), "role1 description")).get
      }
      "return failure if it already exists" in withPersistenceStore { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role("role1", List(TestPermission1.id), "role1 description")).get
        permissionStore.createRole(Role("role1", List(TestPermission1.id), "role1 description")).isFailure shouldBe true
      }
    }

    "adding a role to a user" must {
      "return success" in withPersistenceStore { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role("role1", List(TestPermission1.id), "role1 description")).get

        userStore.createUser(TestUser, "password").get
        domainStore.createDomain(TestDomainFQN, "displayName", TestUser.username).get

        permissionStore.setUserRoles(TestUser.username, TestDomainFQN, List("role1")).get
      }
    }

    "looking up user permissions" must {
      "return correct permissions" in withPersistenceStore { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role("role1", List(TestPermission1.id, TestPermission2.id), "role1 description")).get

        userStore.createUser(TestUser, "password").get
        domainStore.createDomain(TestDomainFQN, "displayName", TestUser.username).get

        permissionStore.setUserRoles(TestUser.username, TestDomainFQN, List("role1")).get

        permissionStore.getAllUserPermissions(TestUser.username, TestDomainFQN).get.toSet shouldBe Set(TestPermission1, TestPermission2)
      }

      "return only the union of permissions" in withPersistenceStore { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role("role1", List(TestPermission1.id, TestPermission2.id), "role1 description")).get
        permissionStore.createRole(Role("role2", List(TestPermission1.id), "role1 description")).get

        userStore.createUser(TestUser, "password").get
        domainStore.createDomain(TestDomainFQN, "displayName", TestUser.username).get

        permissionStore.setUserRoles(TestUser.username, TestDomainFQN, List("role1", "role2")).get

        permissionStore.getAllUserPermissions(TestUser.username, TestDomainFQN).get.size shouldBe 2
      }
    }

    "looking up user roles" must {
      "return correct roles" in withPersistenceStore { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role("role1", List(TestPermission1.id, TestPermission2.id), "role1 description")).get

        userStore.createUser(TestUser, "password").get
        domainStore.createDomain(TestDomainFQN, "displayName", TestUser.username).get

        permissionStore.setUserRoles(TestUser.username, TestDomainFQN, List("role1")).get

        permissionStore.getUserRoles(TestUser.username, TestDomainFQN).get shouldBe UserRoles(TestUser.username, Set("role1"))
      }

      "return user with no roles if user doesn't exist" in withPersistenceStore { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role("role1", List(TestPermission1.id, TestPermission2.id), "role1 description")).get
        permissionStore.createRole(Role("role2", List(TestPermission1.id), "role1 description")).get

        userStore.createUser(TestUser, "password").get
        domainStore.createDomain(TestDomainFQN, "displayName", TestUser.username).get

        permissionStore.getUserRoles(TestUser.username, TestDomainFQN).get shouldBe UserRoles(TestUser.username, Set())
      }

      "return only roles for that user" in withPersistenceStore { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role("role1", List(TestPermission1.id, TestPermission2.id), "role1 description")).get
        permissionStore.createRole(Role("role2", List(TestPermission2.id), "role1 description")).get
        permissionStore.createRole(Role("role3", List(TestPermission1.id), "role1 description")).get

        userStore.createUser(TestUser, "password").get
        userStore.createUser(TestUser2, "password").get
        domainStore.createDomain(TestDomainFQN, "displayName", TestUser.username).get

        permissionStore.setUserRoles(TestUser.username, TestDomainFQN, List("role1")).get
        permissionStore.setUserRoles(TestUser2.username, TestDomainFQN, List("role2", "role3")).get

        permissionStore.getUserRoles(TestUser2.username, TestDomainFQN).get shouldBe UserRoles(TestUser2.username, Set("role2", "role3"))
      }
    }

    "looking up user roles" must {
      "return all user roles for that domain" in withPersistenceStore { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role("role1", List(TestPermission1.id, TestPermission2.id), "role1 description")).get
        permissionStore.createRole(Role("role2", List(TestPermission2.id), "role1 description")).get
        permissionStore.createRole(Role("role3", List(TestPermission1.id), "role1 description")).get

        userStore.createUser(TestUser, "password").get
        userStore.createUser(TestUser2, "password").get

        domainStore.createDomain(TestDomainFQN, "displayName", TestUser.username).get

        permissionStore.setUserRoles(TestUser.username, TestDomainFQN, List("role1")).get
        permissionStore.setUserRoles(TestUser2.username, TestDomainFQN, List("role2", "role3")).get

        permissionStore.getAllUserRoles(TestDomainFQN).get shouldBe Set(UserRoles(TestUser.username, Set("role1")), UserRoles(TestUser2.username, Set("role2", "role3")))
      }
    }
  }
}