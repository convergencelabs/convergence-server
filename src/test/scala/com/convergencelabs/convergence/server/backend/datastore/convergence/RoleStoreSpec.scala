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
import com.convergencelabs.convergence.server.backend.datastore.convergence.RoleStore.{Role, UserRole}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.domain.{DomainDatabase, Namespace}
import com.convergencelabs.convergence.server.model.server.role.{DomainRoleTarget, RoleTargetType, ServerRoleTarget}
import com.convergencelabs.convergence.server.model.server.user.User
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class RoleStoreSpec extends ConvergencePersistenceStoreSpec
  with AnyWordSpecLike with Matchers {
  
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

  private val Role1 = Role(Role1Id, Some(RoleTargetType.Domain), Set(TestPermission1, TestPermission2))
  private val Role2 = Role(Role2Id, None, Set(TestPermission1))
  private val Role3 = Role(Role3Id, None, Set(TestPermission1))
  private val Role4 = Role(Role4Id, None, Set(TestPermission1, TestPermission2))
  private val Role5 = Role(Role5Id, Some(RoleTargetType.Domain), Set(TestPermission2, TestPermission3))

  "A RoleStore" when {
    "saving a role" must {
      "return success" in withTestData { provider =>
        val role = Role(Role1Id, None, Set(TestPermission1))
        provider.roleStore.createRole(role).get

        val read = provider.roleStore.getRole(Role1Id, None).get
        read shouldBe role
      }

      "return failure if it already exists" in withTestData { provider =>
        provider.roleStore.createRole(Role1).get
        provider.roleStore.createRole(Role1).failed.get shouldBe a[DuplicateValueException]
      }
    }

    "adding a role to a user" must {
      "return success" in withTestData { provider =>
        provider.roleStore.createRole(Role1).get
        provider.roleStore.setUserRoleForTarget(TestUser.username, TestDomainTarget, Role1Id).get
      }
    }

    "looking up user permissions" must {
      "return correct permissions" in withTestData { provider =>
        provider.roleStore.createRole(Role1).get
        provider.roleStore.setUserRoleForTarget(TestUser.username, TestDomainTarget, Role1Id).get
        provider.roleStore.getUserPermissionsForTarget(TestUser.username, TestDomainTarget).get shouldBe Set(TestPermission1, TestPermission2)
      }
    }

    "looking up user roles" must {
      "return correct roles" in withTestData { provider =>
        provider.roleStore.createRole(Role1).get
        provider.roleStore.setUserRoleForTarget(TestUser.username, TestDomainTarget, Role1Id).get
        provider.roleStore.getUserRoleForTarget(TestUser.username, TestDomainTarget).get shouldBe Some(Role1)
      }

      "return user with no roles if user doesn't exist" in withTestData { provider =>
        provider.roleStore.createRole(Role1).get
        provider.roleStore.createRole(Role2).get
        provider.roleStore.getUserRoleForTarget(TestUser.username, TestDomainTarget).get shouldBe None
      }

      "return only roles for that user" in withTestData { provider =>
        provider.roleStore.createRole(Role1).get
        provider.roleStore.createRole(Role2).get
        provider.roleStore.createRole(Role3).get
        provider.roleStore.createRole(Role4).get

        provider.roleStore.setUserRoleForTarget(TestUser.username, ServerRoleTarget(), Role4Id).get
        provider.roleStore.setUserRoleForTarget(TestUser2.username, ServerRoleTarget(), Role3Id).get
        provider.roleStore.getUserRoleForTarget(TestUser2.username, ServerRoleTarget()).get shouldBe Some(Role3)
      }
    }

    "looking up user roles" must {
      "return all user roles for that domain" in withTestData { provider =>
        provider.roleStore.createRole(Role1).get
        provider.roleStore.createRole(Role5).get

        provider.roleStore.setUserRoleForTarget(TestUser.username, TestDomainTarget, Role5Id).get
        provider.roleStore.setUserRoleForTarget(TestUser2.username, TestDomainTarget, Role1Id).get

        provider.roleStore.getAllUserRolesForTarget(TestDomainTarget).get shouldBe Map(
          TestUser.username -> UserRole(Role5, TestDomainTarget),
          TestUser2.username -> UserRole(Role1, TestDomainTarget)
        )
      }
    }
  }

  def withTestData(testCode: ConvergencePersistenceProvider => Any): Unit = {
    this.withPersistenceStore { provider =>
      provider.userStore.createUser(TestUser, "password", "token").get
      provider.userStore.createUser(TestUser2, "password", "token2").get
      provider.namespaceStore.createNamespace(TestNamesapce)
      provider.domainStore.createDomain(TestDomainFQN, "displayName", DomainDatabase("db1", "1.0", "", "", "", "")).get
      testCode(provider)
    }
  }
}
