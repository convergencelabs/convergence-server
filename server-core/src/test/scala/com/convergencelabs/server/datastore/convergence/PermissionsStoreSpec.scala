package com.convergencelabs.server.datastore.convergence

import org.scalatest.Matchers
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.convergence.PermissionsStore.Permission
import com.convergencelabs.server.datastore.convergence.PermissionsStore.Role
import com.convergencelabs.server.datastore.convergence.PermissionsStore.UserRoles
import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.Namespace
import com.convergencelabs.server.datastore.DuplicateValueException

case class PermissionStoreSpecStores(
  permissionStore: PermissionsStore,
  userStore: UserStore,
  namespaceStore: NamespaceStore,
  domainStore: DomainStore)

class PermissionsStoreSpec extends PersistenceStoreSpec[PermissionStoreSpecStores](DeltaCategory.Convergence)
  with WordSpecLike with Matchers {
  def createStore(dbProvider: DatabaseProvider): PermissionStoreSpecStores = {
    PermissionStoreSpecStores(new PermissionsStore(dbProvider), new UserStore(dbProvider), new NamespaceStore(dbProvider), new DomainStore(dbProvider))
  }

  val TestUser = User("username1", "test@convergence.com", "username1", "username1", "displayName")
  val TestUser2 = User("username2", "test2@convergence.com", "username2", "username2", "displayName2")

  val TestNamesapce = Namespace("namespace1", "Namespace 1")
  val TestDomainFQN = DomainFqn(TestNamesapce.id, "domain1")

  val TestDomainTarget = DomainPermissionTarget(TestDomainFQN)

  val TestPermission1 = Permission("testId1", "test1", "test1 description")
  val TestPermission2 = Permission("testId2", "test2", "test2 description")

  val Role1Id = "role1"
  val Role2Id = "role2"
  val Role3Id = "role3"

  val Role1 = Role(Role1Id, List(TestPermission1.id, TestPermission2.id), "role1 description")
  val Role2 = Role(Role2Id, List(TestPermission1.id), "role2 description")
  val Role3 = Role(Role3Id, List(TestPermission1.id), "role3 description")

  "A PermissionsStore" when {
    "saving a permission" must {
      "return success" in withTestData { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.createPermission(TestPermission1).get
      }
      "return failure it if already exists" in withTestData { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission1).isFailure shouldBe true
      }
    }

    "calling hasBeenSetup()" must {
      "return true when permissions exist" in withTestData { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.createPermission(TestPermission1).get

        permissionStore.hasBeenSetup().get shouldEqual true
      }
      "return false when no permissions exist" in withTestData { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.hasBeenSetup().get shouldEqual false
      }
    }

    "saving a role" must {
      "return success" in withTestData { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role(Role1Id, List(TestPermission1.id), "role1 description")).get
      }
      "return failure if it already exists" in withTestData { stores =>
        val permissionStore = stores.permissionStore
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role1).get
        permissionStore.createRole(Role1).failed.get shouldBe a[DuplicateValueException]
      }
    }

    "adding a role to a user" must {
      "return success" in withTestData { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, namespaceStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get
        permissionStore.createRole(Role(Role1Id, List(TestPermission1.id), "role1 description")).get
        permissionStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, List("role1")).get
      }
    }

    "looking up user permissions" must {
      "return correct permissions" in withTestData { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, namespaceStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role1).get
        permissionStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, List(Role1Id)).get
        permissionStore.getUserPermissionsForTarget(TestUser.username, TestDomainTarget).get.toSet shouldBe Set(TestPermission1, TestPermission2)
      }

      "return only the union of permissions" in withTestData { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, namespaceStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role1).get
        permissionStore.createRole(Role2).get

        permissionStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, List(Role1Id, Role2Id)).get

        permissionStore.getUserPermissionsForTarget(TestUser.username, TestDomainTarget).get.size shouldBe 2
      }
    }

    "looking up user roles" must {
      "return correct roles" in withTestData { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, namespaceStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role1).get
        permissionStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, List(Role1Id)).get
        permissionStore.getUserRolesForTarget(TestUser.username, TestDomainTarget).get shouldBe Set(Role1)
      }

      "return user with no roles if user doesn't exist" in withTestData { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, namespaceStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role1).get
        permissionStore.createRole(Role2).get

        permissionStore.getUserRolesForTarget(TestUser.username, TestDomainTarget).get shouldBe  Set()
      }

      "return only roles for that user" in withTestData { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, namespaceStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role1).get
        permissionStore.createRole(Role2).get
        permissionStore.createRole(Role3).get

        permissionStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, List(Role1Id)).get
        permissionStore.setUserRolesForTarget(TestUser2.username, TestDomainTarget, List(Role2Id, Role3Id)).get

        permissionStore.getUserRolesForTarget(TestUser2.username, TestDomainTarget).get shouldBe Set(Role2, Role3)
      }
    }

    "looking up user roles" must {
      "return all user roles for that domain" in withTestData { stores =>
        val PermissionStoreSpecStores(permissionStore, userStore, namespaceStore, domainStore) = stores
        permissionStore.createPermission(TestPermission1).get
        permissionStore.createPermission(TestPermission2).get

        permissionStore.createRole(Role1).get
        permissionStore.createRole(Role2).get
        permissionStore.createRole(Role3).get

        permissionStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, List(Role1Id)).get
        permissionStore.setUserRolesForTarget(TestUser2.username, TestDomainTarget, List(Role2Id, Role3Id)).get

        permissionStore.getAllUserRolesForTarget(TestDomainTarget).get shouldBe Set(UserRoles(TestUser.username, Set(Role1Id)), UserRoles(TestUser2.username, Set(Role2Id, Role3Id)))
      }
    }
  }

  def withTestData(testCode: PermissionStoreSpecStores => Any): Unit = {
    this.withPersistenceStore { stores =>
      stores.userStore.createUser(TestUser, "password", "token").get
      stores.userStore.createUser(TestUser2, "password", "token2").get
      stores.namespaceStore.createNamespace(TestNamesapce)
      stores.domainStore.createDomain(TestDomainFQN, "displayName", DomainDatabase("db1", "", "", "", "")).get
      testCode(stores)
    }
  }
}