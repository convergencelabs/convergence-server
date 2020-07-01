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

package com.convergencelabs.convergence.server.backend.datastore.convergence

import com.convergencelabs.convergence.server.backend.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.backend.datastore.convergence.RoleStore.{Role, UserRole, UserRoles}
import com.convergencelabs.convergence.server.backend.datastore.convergence.UserStore.User
import com.convergencelabs.convergence.server.backend.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.db.schema.DeltaCategory
import com.convergencelabs.convergence.server.model.domain.{DomainDatabase, DomainId, Namespace}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

case class RoleStoreSpecStores(
  roleStore: RoleStore,
  userStore: UserStore,
  namespaceStore: NamespaceStore,
  domainStore: DomainStore)

class RoleStoreSpec extends PersistenceStoreSpec[RoleStoreSpecStores](DeltaCategory.Convergence)
  with AnyWordSpecLike with Matchers {
  def createStore(dbProvider: DatabaseProvider): RoleStoreSpecStores = {
    RoleStoreSpecStores(new RoleStore(dbProvider), new UserStore(dbProvider), new NamespaceStore(dbProvider), new DomainStore(dbProvider))
  }

  private val TestUser = User("username1", "test@convergence.com", "username1", "username1", "displayName", None)
  private val TestUser2 = User("username2", "test2@convergence.com", "username2", "username2", "displayName2", None)

  private val TestNamesapce = Namespace("namespace1", "Namespace 1", userNamespace = false)
  private val TestDomainFQN = DomainId(TestNamesapce.id, "domain1")

  private val TestDomainTarget = DomainRoleTarget(TestDomainFQN)

  private val TestPermission1 = "testId1"
  private val TestPermission2 = "testId2"
  private val TestPermission3 = "testId3"

  private val Role1Id = "role1"
  private val Role2Id = "role2"
  private val Role3Id = "role3"
  private val Role4Id = "role4"
  private val Role5Id = "role5"

  private val Role1 = Role(Role1Id, Some(RoleTargetType.Domain),  Set(TestPermission1, TestPermission2))
  private val Role2 = Role(Role2Id, None, Set(TestPermission1))
  private val Role3 = Role(Role3Id, None, Set(TestPermission1))
  private val Role4 = Role(Role4Id, None, Set(TestPermission1, TestPermission2))
  private val Role5 = Role(Role5Id, Some(RoleTargetType.Domain), Set(TestPermission2, TestPermission3))

  "A RoleStore" when {
    "saving a role" must {
      "return success" in withTestData { stores =>
        val roleStore = stores.roleStore
        val role = Role(Role1Id, None, Set(TestPermission1))
        roleStore.createRole(role).get
        
        val read = roleStore.getRole(Role1Id, None).get
        read shouldBe role
      }
      
      "return failure if it already exists" in withTestData { stores =>
        val roleStore = stores.roleStore
        roleStore.createRole(Role1).get
        roleStore.createRole(Role1).failed.get shouldBe a[DuplicateValueException]
      }
    }

    "adding a role to a user" must {
      "return success" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, _, _, _) = stores
        roleStore.createRole(Role1).get
        roleStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, Set(Role1Id)).get
      }
    }

    "looking up user permissions" must {
      "return correct permissions" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, _, _, _) = stores
        roleStore.createRole(Role1).get
        roleStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, Set(Role1Id)).get
        roleStore.getUserPermissionsForTarget(TestUser.username, TestDomainTarget).get shouldBe Set(TestPermission1, TestPermission2)
      }

      "return only the union of permissions" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, _, _, _) = stores
        roleStore.createRole(Role4).get
        roleStore.createRole(Role2).get
        roleStore.setUserRolesForTarget(TestUser.username, ServerRoleTarget(), Set(Role4Id, Role2Id)).get
        roleStore.getUserPermissionsForTarget(TestUser.username, ServerRoleTarget()).get.size shouldBe 2
      }
    }

    "looking up user roles" must {
      "return correct roles" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, _, _, _) = stores
        roleStore.createRole(Role1).get
        roleStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, Set(Role1Id)).get
        roleStore.getUserRolesForTarget(TestUser.username, TestDomainTarget).get shouldBe Set(Role1)
      }

      "return user with no roles if user doesn't exist" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, _, _, _) = stores
        roleStore.createRole(Role1).get
        roleStore.createRole(Role2).get
        roleStore.getUserRolesForTarget(TestUser.username, TestDomainTarget).get shouldBe  Set()
      }

      "return only roles for that user" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, _, _, _) = stores
        roleStore.createRole(Role1).get
        roleStore.createRole(Role2).get
        roleStore.createRole(Role3).get
        roleStore.createRole(Role4).get
        
        roleStore.setUserRolesForTarget(TestUser.username, ServerRoleTarget(), Set(Role4Id)).get
        roleStore.setUserRolesForTarget(TestUser2.username, ServerRoleTarget(), Set(Role2Id, Role3Id)).get
        roleStore.getUserRolesForTarget(TestUser2.username, ServerRoleTarget()).get shouldBe Set(Role2, Role3)
      }
    }

    "looking up user roles" must {
      "return all user roles for that domain" in withTestData { stores =>
        val RoleStoreSpecStores(roleStore, _, _, _) = stores
        roleStore.createRole(Role1).get
        roleStore.createRole(Role5).get

        roleStore.setUserRolesForTarget(TestUser.username, TestDomainTarget, Set(Role5Id)).get
        roleStore.setUserRolesForTarget(TestUser2.username, TestDomainTarget, Set(Role1Id)).get

        roleStore.getAllUserRolesForTarget(TestDomainTarget).get shouldBe Set(
            UserRoles(TestUser.username, Set(UserRole(Role5, TestDomainTarget))), 
            UserRoles(TestUser2.username, Set(UserRole(Role1, TestDomainTarget))))
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