package com.convergencelabs.server.datastore.convergence

import org.scalatest.Matchers
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.convergence.RoleStore.Role
import com.convergencelabs.server.datastore.convergence.RoleStore.UserRoles
import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.Namespace
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.convergence.RoleStore.UserRole

case class RoleStoreSpecStores(
  roleStore: RoleStore,
  userStore: UserStore,
  namespaceStore: NamespaceStore,
  domainStore: DomainStore)

class RoleStoreSpec extends PersistenceStoreSpec[RoleStoreSpecStores](DeltaCategory.Convergence)
  with WordSpecLike with Matchers {
  def createStore(dbProvider: DatabaseProvider): RoleStoreSpecStores = {
    RoleStoreSpecStores(new RoleStore(dbProvider), new UserStore(dbProvider), new NamespaceStore(dbProvider), new DomainStore(dbProvider))
  }

  val TestUser = User("username1", "test@convergence.com", "username1", "username1", "displayName")
  val TestUser2 = User("username2", "test2@convergence.com", "username2", "username2", "displayName2")

  val TestNamesapce = Namespace("namespace1", "Namespace 1")
  val TestDomainFQN = DomainFqn(TestNamesapce.id, "domain1")

  val TestDomainTarget = DomainRoleTarget(TestDomainFQN)

  val TestPermission1 = "testId1"
  val TestPermission2 = "testId2"

  val Role1Id = "role1"
  val Role2Id = "role2"
  val Role3Id = "role3"

  val Role1 = Role(Role1Id, Some(RoleTargetType.Domain),  Set(TestPermission1, TestPermission2))
  val Role2 = Role(Role2Id, None, Set(TestPermission1))
  val Role3 = Role(Role3Id, None, Set(TestPermission1))

  "A PermissionsStore" when {
    "saving a role" must {
      "return success" in withTestData { stores =>
        val roleStore = stores.roleStore
        roleStore.createRole(Role(Role1Id, None, Set(TestPermission1))).get
      }
      "return failure if it already exists" in withTestData { stores =>
        val roleStore = stores.roleStore
        roleStore.createRole(Role1).get
        roleStore.createRole(Role1).failed.get shouldBe a[DuplicateValueException]
      }
    }

    "adding a role to a user" must {
      "return success" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, userStore, namespaceStore, domainStore) = stores
        roleStore.createRole(Role(Role1Id, None, Set(TestPermission1))).get
        roleStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, Set("role1")).get
      }
    }

    "looking up user permissions" must {
      "return correct permissions" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, userStore, namespaceStore, domainStore) = stores
        roleStore.createRole(Role1).get
        roleStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, Set(Role1Id)).get
        roleStore.getUserPermissionsForTarget(TestUser.username, TestDomainTarget).get.toSet shouldBe Set(TestPermission1, TestPermission2)
      }

      "return only the union of permissions" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, userStore, namespaceStore, domainStore) = stores
        roleStore.createRole(Role1).get
        roleStore.createRole(Role2).get
        roleStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, Set(Role1Id, Role2Id)).get
        roleStore.getUserPermissionsForTarget(TestUser.username, TestDomainTarget).get.size shouldBe 2
      }
    }

    "looking up user roles" must {
      "return correct roles" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, userStore, namespaceStore, domainStore) = stores
        roleStore.createRole(Role1).get
        roleStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, Set(Role1Id)).get
        roleStore.getUserRolesForTarget(TestUser.username, TestDomainTarget).get shouldBe Set(Role1)
      }

      "return user with no roles if user doesn't exist" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, userStore, namespaceStore, domainStore) = stores
        roleStore.createRole(Role1).get
        roleStore.createRole(Role2).get
        roleStore.getUserRolesForTarget(TestUser.username, TestDomainTarget).get shouldBe  Set()
      }

      "return only roles for that user" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, userStore, namespaceStore, domainStore) = stores
        roleStore.createRole(Role1).get
        roleStore.createRole(Role2).get
        roleStore.createRole(Role3).get
        
        roleStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, Set(Role1Id)).get
        roleStore.setUserRolesForTarget(TestUser2.username, TestDomainTarget, Set(Role2Id, Role3Id)).get
        roleStore.getUserRolesForTarget(TestUser2.username, TestDomainTarget).get shouldBe Set(Role2, Role3)
      }
    }

    "looking up user roles" must {
      "return all user roles for that domain" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, userStore, namespaceStore, domainStore) = stores
        roleStore.createRole(Role1).get
        roleStore.createRole(Role2).get
        roleStore.createRole(Role3).get

        roleStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, Set(Role1Id)).get
        roleStore.setUserRolesForTarget(TestUser2.username, TestDomainTarget, Set(Role2Id, Role3Id)).get

        roleStore.getAllUserRolesForTarget(TestDomainTarget).get shouldBe Set(
            UserRoles(TestUser.username, Set(UserRole(Role1, TestDomainTarget))), 
            UserRoles(TestUser2.username, Set(UserRole(Role2, TestDomainTarget), UserRole(Role3, TestDomainTarget))))
      }
    }
  }

  def withTestData(testCode: RoleStoreSpecStores => Any): Unit = {
    this.withPersistenceStore { stores =>
      stores.userStore.createUser(TestUser, "password", "token").get
      stores.userStore.createUser(TestUser2, "password", "token2").get
      stores.namespaceStore.createNamespace(TestNamesapce)
      stores.domainStore.createDomain(TestDomainFQN, "displayName", DomainDatabase("db1", "", "", "", "")).get
      testCode(stores)
    }
  }
}