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

import com.convergencelabs.convergence.server.backend.datastore.domain.permissions._
import com.convergencelabs.convergence.server.model.domain.chat.{ChatMembership, ChatType}
import com.convergencelabs.convergence.server.model.domain.group.UserGroup
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.Instant

class PermissionsStoreSpec
  extends DomainPersistenceStoreSpec
    with AnyWordSpecLike
    with Matchers {

  private val chat1Id = "channel1"
  private val chat2Id = "channel2"

  private val user1 = DomainUserId.normal("user1")
  private val user2 = DomainUserId.normal("user2")
  private val user3 = DomainUserId.normal("user3")

  private val domainUser1 = DomainUser(user1, None, None, None, None, None)
  private val domainUser2 = DomainUser(user2, None, None, None, None, None)
  private val domainUser3 = DomainUser(user3, None, None, None, None, None)

  private val group1 = "group1"
  private val group2 = "group2"

  private val userGroup1 = UserGroup(group1, group1, Set(user1, user2))
  private val userGroup2 = UserGroup(group2, group2, Set(user2, user3))

  private val permission1 = "permission1"
  private val permission2 = "permission2"
  private val permission3 = "permission3"

  "A PermissionsStore" when {
    "creating adding permissions" must {
      "succeed when creating global permission" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission1), GlobalPermissionTarget).get
      }

      "succeed when creating world permission for channel" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission1), ChatPermissionTarget(chat1Id)).get
      }

      "succeed when creating group permission" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1), group1, GlobalPermissionTarget).get
      }

      "succeed when creating group permission for channel" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1), group1, ChatPermissionTarget(chat1Id)).get
      }

      "succeed when creating user permission" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1), user1, GlobalPermissionTarget).get
      }

      "succeed when creating user permission for channel" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1), user1, ChatPermissionTarget(chat1Id)).get
      }
    }

    "checking if user has global permission" must {
      "return false when no permissions are set" in withTestData { provider =>
        val hasPermission = provider.permissionsStore.userHasPermission(user1, GlobalPermissionTarget, permission1).get
        hasPermission shouldBe false
      }

      "return false when irrelevant permissions are set" in withTestData { provider =>
        // Create some other permissions that should not be picked up.
        provider.permissionsStore.addPermissionsForWorld(Set(permission2), GlobalPermissionTarget).get
        provider.permissionsStore.addPermissionsForUser(Set(permission2), user1, GlobalPermissionTarget).get
        provider.permissionsStore.addPermissionsForGroup(Set(permission2), group1, GlobalPermissionTarget).get

        val hasPermission = provider.permissionsStore.userHasPermission(user1, GlobalPermissionTarget, permission1).get
        hasPermission shouldBe false
      }

      "return true when global world permission is set" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission1), GlobalPermissionTarget).get
        val hasPermission = provider.permissionsStore.userHasPermission(user1, ChatPermissionTarget(chat1Id), permission1).get
        hasPermission shouldBe true
      }

      "return false when group permission is set, but user is not in group" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1), group1, GlobalPermissionTarget).get
        val hasPermission = provider.permissionsStore.userHasPermission(user3, GlobalPermissionTarget, permission1).get
        hasPermission shouldBe false
      }

      "return true when group permission is set and user is in group" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1), group1, GlobalPermissionTarget).get
        val hasPermission = provider.permissionsStore.userHasPermission(user1, GlobalPermissionTarget, permission1).get
        hasPermission shouldBe true
      }

      "return true when user permission is set globally" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1), user1, GlobalPermissionTarget).get
        val hasPermission = provider.permissionsStore.userHasPermission(user1, GlobalPermissionTarget, permission1).get
        hasPermission shouldBe true
      }
    }

    "checking if user has permission for a target" must {
      "return false when no permissions are set " in withTestData { provider =>
        val hasPermission = provider.permissionsStore.userHasPermission(user1, ChatPermissionTarget(chat1Id), permission1).get
        hasPermission shouldBe false
      }

      "return false when irrelevant permissions are set " in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission2), ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForWorld(Set(permission2), GlobalPermissionTarget).get
        provider.permissionsStore.addPermissionsForUser(Set(permission2), user1, ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForUser(Set(permission2), user1, GlobalPermissionTarget).get
        provider.permissionsStore.addPermissionsForGroup(Set(permission2), group1, ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForGroup(Set(permission2), group1, GlobalPermissionTarget).get

        val hasPermission = provider.permissionsStore.userHasPermission(user1, ChatPermissionTarget(chat1Id), permission1).get
        hasPermission shouldBe false
      }

      //
      // Global
      //
      "return true when permission is set globally for the user " in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1), user1, GlobalPermissionTarget).get
        val hasPermission = provider.permissionsStore.userHasPermission(user1, ChatPermissionTarget(chat1Id), permission1).get
        hasPermission shouldBe true
      }

      "return true when permission is set globally for a group the user is in " in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1), group1, GlobalPermissionTarget).get
        val hasPermission = provider.permissionsStore.userHasPermission(user1, ChatPermissionTarget(chat1Id), permission1).get
        hasPermission shouldBe true
      }

      "return true when permission is set globally for a group the user is not in " in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1), group1, GlobalPermissionTarget).get
        val hasPermission = provider.permissionsStore.userHasPermission(user3, ChatPermissionTarget(chat1Id), permission1).get
        hasPermission shouldBe false
      }

      //
      // World
      //
      "return true when world permission for chat is set" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission1), ChatPermissionTarget(chat1Id)).get
        val hasPermission = provider.permissionsStore.userHasPermission(user1, ChatPermissionTarget(chat1Id), permission1).get
        hasPermission shouldBe true
      }

      //
      // Group
      //
      "return true when group permission is set and the user is in the group" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1), group1, ChatPermissionTarget(chat1Id)).get
        val hasPermission = provider.permissionsStore.userHasPermission(user1, ChatPermissionTarget(chat1Id), permission1).get
        hasPermission shouldBe true
      }

      "return false when group permission is set and the user is not in the group" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1), group1, ChatPermissionTarget(chat1Id)).get
        val hasPermission = provider.permissionsStore.userHasPermission(user3, ChatPermissionTarget(chat1Id), permission1).get
        hasPermission shouldBe false
      }

      //
      // User
      //

      "return true when user permission is set for the user" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1), user1, ChatPermissionTarget(chat1Id)).get
        val hasPermission = provider.permissionsStore.userHasPermission(user1, ChatPermissionTarget(chat1Id), permission1).get
        hasPermission shouldBe true
      }

      "return false when user permission is set for another user" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1), user2, ChatPermissionTarget(chat1Id)).get
        val hasPermission = provider.permissionsStore.userHasPermission(user1, ChatPermissionTarget(chat1Id), permission1).get
        hasPermission shouldBe false
      }
    }

    "managing all permissions for a target" must {
      "correctly add all world, user, and group permissions" in withTestData { provider =>
        val target = ChatPermissionTarget(chat1Id)

        val user1Permissions = Set(permission1, permission2)
        val user2Permissions = Set(permission3)
        val user = Map(user1 -> user1Permissions, user2 ->user2Permissions)

        val group1Permissions =  Set(permission2)
        val group2Permissions =  Set(permission3)
        val group = Map(group1 -> group1Permissions, group2 -> group2Permissions)

        val world = Set(permission1, permission2)

        provider.permissionsStore.addPermissionsForTarget(target, user, group, world).get

        provider.permissionsStore.getPermissionsForUser(user1, target).get shouldBe user1Permissions
        provider.permissionsStore.getPermissionsForUser(user2, target).get shouldBe user2Permissions

        provider.permissionsStore.getPermissionsForGroup(group1, target).get shouldBe group1Permissions
        provider.permissionsStore.getPermissionsForGroup(group2, target).get shouldBe group2Permissions

        provider.permissionsStore.getPermissionsForWorld(target).get shouldBe world
      }

      "correctly remove all world, user, and group permissions" in withTestData { provider =>
        val target = ChatPermissionTarget(chat1Id)

        val user1Permissions = Set(permission1, permission2)
        val user2Permissions = Set(permission3)
        val user = Map(user1 -> user1Permissions, user2-> user2Permissions)

        val group1Permissions =  Set(permission2)
        val group2Permissions =  Set(permission3)
        val group = Map(group1 -> group1Permissions, group2 -> group2Permissions)

        val world = Set(permission1, permission2)

        provider.permissionsStore.addPermissionsForTarget(target, user, group, world).get


        val userRemove = Map(user1 -> Set(permission1))
        val groupRemove = Map(group1 -> Set(permission2))
        val worldRemove = Set(permission2)
        provider.permissionsStore.removePermissionsForTarget(target, userRemove, groupRemove, worldRemove)

        provider.permissionsStore.getPermissionsForUser(user1, target).get shouldBe Set(permission2)
        provider.permissionsStore.getPermissionsForUser(user2, target).get shouldBe user2Permissions

        provider.permissionsStore.getPermissionsForGroup(group1, target).get shouldBe Set()
        provider.permissionsStore.getPermissionsForGroup(group2, target).get shouldBe group2Permissions

        provider.permissionsStore.getPermissionsForWorld(target).get shouldBe Set(permission1)
      }

      "correctly set all world, user, and group permissions" in withTestData { provider =>
        val target = ChatPermissionTarget(chat1Id)

        val user1Permissions = Set(permission1, permission2)
        val user2Permissions = Set(permission3)
        val user = Map(user1 -> user1Permissions, user2 -> user2Permissions)

        val group1Permissions =  Set(permission2)
        val group2Permissions =  Set(permission3)
        val group = Map(group1 -> group1Permissions, group2 -> group2Permissions)

        val world = Set(permission1, permission2)

        provider.permissionsStore.addPermissionsForTarget(target, user, group, world).get

        val userSet = Some(Map(user1 -> Set(permission1)))
        val groupSet = Some(Map(group1 -> Set(permission2)))
        val worldSet = Some(Set(permission2))
        provider.permissionsStore.setPermissionsForTarget(target, userSet, replaceUsers = true,  groupSet, replaceGroups = true, worldSet)

        provider.permissionsStore.getPermissionsForUser(user1, target).get shouldBe Set(permission1)
        provider.permissionsStore.getPermissionsForUser(user2, target).get shouldBe user2Permissions

        provider.permissionsStore.getPermissionsForGroup(group1, target).get shouldBe Set(permission2)
        provider.permissionsStore.getPermissionsForGroup(group2, target).get shouldBe group2Permissions

        provider.permissionsStore.getPermissionsForWorld(target).get shouldBe Set(permission2)
      }

      "correctly set all world, user, and group permissions when Nones are supplied" in withTestData { provider =>
        val target = ChatPermissionTarget(chat1Id)

        val user1Permissions = Set(permission1, permission2)
        val user2Permissions = Set(permission3)
        val user = Map(user1 -> user1Permissions, user2 -> user2Permissions)

        val group1Permissions =  Set(permission2)
        val group2Permissions =  Set(permission3)
        val group = Map(group1 -> group1Permissions, group2 -> group2Permissions)

        val world = Set(permission1, permission2)

        provider.permissionsStore.addPermissionsForTarget(target, user, group, world).get

        provider.permissionsStore.setPermissionsForTarget(target, None, replaceUsers = false, None, replaceGroups = false, None).get

        provider.permissionsStore.getPermissionsForUser(user1, target).get shouldBe user1Permissions
        provider.permissionsStore.getPermissionsForUser(user2, target).get shouldBe user2Permissions

        provider.permissionsStore.getPermissionsForGroup(group1, target).get shouldBe group1Permissions
        provider.permissionsStore.getPermissionsForGroup(group2, target).get shouldBe group2Permissions

        provider.permissionsStore.getPermissionsForWorld(target).get shouldBe world
      }

      "correctly set partial user permissions" in withTestData { provider =>
        val target = ChatPermissionTarget(chat1Id)

        val user1Permissions = Set(permission1, permission2)
        val user2Permissions = Set(permission3)
        val user3Permissions = Set(permission2, permission3)
        val initialUserPermissions = Map(user1 -> user1Permissions, user2 -> user2Permissions, user3 -> user3Permissions)

        provider.permissionsStore.addPermissionsForTarget(target, initialUserPermissions, Map(), Set()).get

        provider.permissionsStore.getPermissionsForUser(user1, target).get shouldBe user1Permissions
        provider.permissionsStore.getPermissionsForUser(user2, target).get shouldBe user2Permissions
        provider.permissionsStore.getPermissionsForUser(user3, target).get shouldBe user3Permissions

        val userSet = Some(Map(user1 -> Set(permission1), user2 -> Set[String]()))
        provider.permissionsStore.setPermissionsForTarget(target, userSet, replaceUsers = false,  None, replaceGroups = false, None)

        provider.permissionsStore.getPermissionsForUser(user1, target).get shouldBe Set(permission1)
        provider.permissionsStore.getPermissionsForUser(user2, target).get shouldBe Set()
        provider.permissionsStore.getPermissionsForUser(user3, target).get shouldBe user3Permissions
      }
    }

    "aggregating user permissions for target" must {
      "return an empty set if not permissions are set" in withTestData { provider =>
        val hasPermission = provider.permissionsStore.resolveUserPermissionsForTarget(user1, ChatPermissionTarget(chat1Id), Set(permission1, permission2)).get
        hasPermission shouldBe Set()
      }

      "return correct permissions for globally set user permissions" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission3), user1, GlobalPermissionTarget).get
        val hasPermission = provider.permissionsStore.resolveUserPermissionsForTarget(user1, ChatPermissionTarget(chat1Id), Set(permission1, permission2)).get
        hasPermission shouldBe Set(permission1)
      }

      "return multiple correct permissions for globally set user permissions" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, GlobalPermissionTarget).get
        val hasPermission = provider.permissionsStore.resolveUserPermissionsForTarget(user1, ChatPermissionTarget(chat1Id), Set(permission1, permission2)).get
        hasPermission shouldBe Set(permission1, permission2)
      }

      "return correct permissions for globally set group permissions" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1, permission3), group1, GlobalPermissionTarget).get
        // Ensure granting to group 2 doesn't make permission 2 get in.
        provider.permissionsStore.addPermissionsForGroup(Set(permission2), group2, GlobalPermissionTarget).get
        val hasPermission = provider.permissionsStore.resolveUserPermissionsForTarget(user1, ChatPermissionTarget(chat1Id), Set(permission1, permission2)).get
        hasPermission shouldBe Set(permission1)
      }

      "return correct permissions for globally set world permissions" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission1, permission3), GlobalPermissionTarget).get
        val hasPermission = provider.permissionsStore.resolveUserPermissionsForTarget(user1, ChatPermissionTarget(chat1Id), Set(permission1, permission2)).get
        hasPermission shouldBe Set(permission1)
      }

      "return multiple correct permissions for targeted set user permissions" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, ChatPermissionTarget(chat1Id)).get
        val hasPermission = provider.permissionsStore.resolveUserPermissionsForTarget(user1, ChatPermissionTarget(chat1Id), Set(permission1, permission2)).get
        hasPermission shouldBe Set(permission1, permission2)
      }

      "return correct permissions for targeted set group permissions" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1, permission3), group1, ChatPermissionTarget(chat1Id)).get
        // Ensure granting to group 2 doesn't make permission 2 get in.
        provider.permissionsStore.addPermissionsForGroup(Set(permission2), group2, ChatPermissionTarget(chat1Id)).get
        val hasPermission = provider.permissionsStore.resolveUserPermissionsForTarget(user1, ChatPermissionTarget(chat1Id), Set(permission1, permission2)).get
        hasPermission shouldBe Set(permission1)
      }

      "return correct permissions for targeted set world permissions" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission1, permission3), ChatPermissionTarget(chat1Id)).get
        val hasPermission = provider.permissionsStore.resolveUserPermissionsForTarget(user1, ChatPermissionTarget(chat1Id), Set(permission1, permission2)).get
        hasPermission shouldBe Set(permission1)
      }
    }

    "retrieving permissions" must {
      "return correct global permissions" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission1, permission2), GlobalPermissionTarget).get
        val globalPermissions = provider.permissionsStore.getPermissionsForWorld(GlobalPermissionTarget).get
        globalPermissions shouldBe Set(permission1, permission2)
      }

      "return correct world permissions for channel" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission1, permission2), ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForWorld(Set(permission3), ChatPermissionTarget(chat2Id)).get
        val worldPermissions = provider.permissionsStore.getPermissionsForWorld(ChatPermissionTarget(chat1Id)).get
        worldPermissions shouldBe Set(permission1, permission2)
      }

      "return correct user permissions with no target" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, GlobalPermissionTarget).get
        provider.permissionsStore.addPermissionsForUser(Set(permission3), user2, GlobalPermissionTarget).get
        val globalPermissions = provider.permissionsStore.getUserPermissionsForTarget(GlobalPermissionTarget).get
        globalPermissions shouldBe Set(
          UserPermission(user1, permission1),
          UserPermission(user1, permission2),
          UserPermission(user2, permission3))
      }
      "return correct user permissions for channel" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForUser(Set(permission3), user2, ChatPermissionTarget(chat2Id)).get
        val userPermissions = provider.permissionsStore.getUserPermissionsForTarget(ChatPermissionTarget(chat1Id)).get
        userPermissions shouldBe Set(UserPermission(user1, permission1), UserPermission(user1, permission2))
      }

      "return correct group permissions" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1, permission2), group1, GlobalPermissionTarget).get
        provider.permissionsStore.addPermissionsForGroup(Set(permission3), group2, GlobalPermissionTarget).get
        val globalPermissions = provider.permissionsStore.getGroupPermissionsForTarget(GlobalPermissionTarget).get
        globalPermissions shouldBe Set(
          GroupPermission(group1, permission1),
          GroupPermission(group1, permission2),
          GroupPermission(group2, permission3))
      }
      "return correct group permissions for channel" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1, permission2), group1, ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForGroup(Set(permission3), group2, ChatPermissionTarget(chat2Id)).get
        val groupPermissions = provider.permissionsStore.getGroupPermissionsForTarget(ChatPermissionTarget(chat1Id)).get
        groupPermissions shouldBe Set(GroupPermission(group1, permission1), GroupPermission(group1, permission2))
      }
    }

    "setting permissions" must {
      "set the correct permissions for a user and target" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, ChatPermissionTarget(chat2Id)).get
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user2, ChatPermissionTarget(chat1Id)).get

        provider.permissionsStore.getPermissionsForUser(user1, ChatPermissionTarget(chat1Id)).get shouldBe Set(permission1, permission2)

        provider.permissionsStore.setPermissionsForUser(Set(permission2, permission3), user1, ChatPermissionTarget(chat1Id)).get

        // Channel 1 permissions properly set.
        provider.permissionsStore.getPermissionsForUser(user1, ChatPermissionTarget(chat1Id)).get shouldBe Set(permission2, permission3)

        // User 1's permissions for channel 2 not changed.
        provider.permissionsStore.getPermissionsForUser(user1, ChatPermissionTarget(chat2Id)).get shouldBe Set(permission1, permission2)

        // User 2's permissions for channel 1 not changed.
        provider.permissionsStore.getPermissionsForUser(user2, ChatPermissionTarget(chat1Id)).get shouldBe Set(permission1, permission2)
      }

      "set the correct permissions for a group and target" in withTestData { provider =>
        val permissionsStore = provider.permissionsStore
        val target1 = ChatPermissionTarget(chat1Id)
        val target2 = ChatPermissionTarget(chat2Id)

        permissionsStore.addPermissionsForGroup(Set(permission1, permission2), group1, target1).get
        permissionsStore.addPermissionsForGroup(Set(permission1, permission2), group1, target2).get
        permissionsStore.addPermissionsForGroup(Set(permission1, permission2), group2, target1).get

        permissionsStore.getPermissionsForGroup(group1, target1).get shouldBe Set(permission1, permission2)

        permissionsStore.setPermissionsForGroup(Set(permission2, permission3), group1, target1).get

        // Channel 1 permissions properly set.
        permissionsStore.getPermissionsForGroup(group1, target1).get shouldBe Set(permission2, permission3)

        // Group 1's permissions for channel 2 not changed.
        permissionsStore.getPermissionsForGroup(group1, target2).get shouldBe Set(permission1, permission2)

        // Group 2's permissions for channel 1 not changed.
        permissionsStore.getPermissionsForGroup(group2, target1).get shouldBe Set(permission1, permission2)
      }

      "set the correct permissions for a world and target" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission1, permission2), ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForWorld(Set(permission1, permission2), ChatPermissionTarget(chat2Id)).get

        provider.permissionsStore.getPermissionsForWorld(ChatPermissionTarget(chat1Id))
          .get shouldBe Set(permission1, permission2)

        provider.permissionsStore.setPermissionsForWorld(Set(permission2, permission3), ChatPermissionTarget(chat1Id)).get

        // Channel 1 permissions properly set.
        provider.permissionsStore.getPermissionsForWorld(ChatPermissionTarget(chat1Id))
          .get shouldBe Set(permission2, permission3)

        // World permissions for channel 2 not changed.
        provider.permissionsStore.getPermissionsForWorld(ChatPermissionTarget(chat2Id))
          .get shouldBe Set(permission1, permission2)
      }
    }

    "removing permissions" must {
      "remove the correct permissions for a user and target" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, ChatPermissionTarget(chat2Id)).get
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user2, ChatPermissionTarget(chat1Id)).get

        provider.permissionsStore.getPermissionsForUser(user1, ChatPermissionTarget(chat1Id)).get shouldBe Set(permission1, permission2)

        provider.permissionsStore.removePermissionsForUser(Set(permission2), user1, ChatPermissionTarget(chat1Id)).get

        // Channel 1 permissions properly set.
        provider.permissionsStore.getPermissionsForUser(user1, ChatPermissionTarget(chat1Id)).get shouldBe Set(permission1)

        // User 1's permissions for channel 2 not changed.
        provider.permissionsStore.getPermissionsForUser(user1, ChatPermissionTarget(chat2Id)).get shouldBe Set(permission1, permission2)

        // User 2's permissions for channel 1 not changed.
        provider.permissionsStore.getPermissionsForUser(user2, ChatPermissionTarget(chat1Id)).get shouldBe Set(permission1, permission2)
      }

      "remove the correct permissions for a group and target" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForGroup(Set(permission1, permission2), group1, ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForGroup(Set(permission1, permission2), group2, ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForGroup(Set(permission1, permission2), group1, ChatPermissionTarget(chat2Id)).get

        provider.permissionsStore.getPermissionsForGroup(group1, ChatPermissionTarget(chat1Id)).get shouldBe Set(permission1, permission2)

        provider.permissionsStore.removePermissionsForGroup(Set(permission2), group1, ChatPermissionTarget(chat1Id)).get

        // Channel 1 permissions properly set.
        provider.permissionsStore.getPermissionsForGroup(group1, ChatPermissionTarget(chat1Id)).get shouldBe Set(permission1)

        // Group 1's permissions for channel 2 not changed.
        provider.permissionsStore.getPermissionsForGroup(group1, ChatPermissionTarget(chat2Id)).get shouldBe Set(permission1, permission2)

        // Group 2's permissions for channel 1 not changed.
        provider.permissionsStore.getPermissionsForGroup(group2, ChatPermissionTarget(chat1Id)).get shouldBe Set(permission1, permission2)
      }

      "remove the correct permissions for a world and target" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission1, permission2), ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForWorld(Set(permission1, permission2), ChatPermissionTarget(chat2Id)).get

        provider.permissionsStore.getPermissionsForWorld(ChatPermissionTarget(chat1Id))
          .get shouldBe Set(permission1, permission2)

        provider.permissionsStore.removePermissionsForWorld(Set(permission2), ChatPermissionTarget(chat1Id)).get

        // Channel 1 permissions properly set.
        provider.permissionsStore.getPermissionsForWorld(ChatPermissionTarget(chat1Id))
          .get shouldBe Set(permission1)

        // World permissions for channel 2 not changed.
        provider.permissionsStore.getPermissionsForWorld(ChatPermissionTarget(chat2Id))
          .get shouldBe Set(permission1, permission2)
      }

      "remove all permissions for target" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForWorld(Set(permission1, permission2), ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForWorld(Set(permission1, permission2), ChatPermissionTarget(chat2Id)).get

        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, ChatPermissionTarget(chat2Id)).get

        provider.permissionsStore.addPermissionsForGroup(Set(permission1, permission2), group1, ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForGroup(Set(permission1, permission2), group1, ChatPermissionTarget(chat2Id)).get

        provider.permissionsStore.removeAllPermissionsForTarget(ChatPermissionTarget(chat1Id)).get

        // Validate all permissions for chat 1 were removed.
        provider.permissionsStore.getUserPermissionsForTarget(ChatPermissionTarget(chat1Id)).get shouldBe Set()
        provider.permissionsStore.getGroupPermissionsForTarget(ChatPermissionTarget(chat1Id)).get shouldBe Set()
        provider.permissionsStore.getPermissionsForWorld(ChatPermissionTarget(chat1Id)).get shouldBe Set()

        // Validate all permissions for chat 2 were not removed.
        provider.permissionsStore.getUserPermissionsForTarget(ChatPermissionTarget(chat2Id)).get shouldBe
          Set(UserPermission(user1, permission1), UserPermission(user1, permission2))

        provider.permissionsStore.getGroupPermissionsForTarget(ChatPermissionTarget(chat2Id)).get shouldBe
          Set(GroupPermission(group1, permission1), GroupPermission(group1, permission2))

        provider.permissionsStore.getPermissionsForWorld(ChatPermissionTarget(chat2Id)).get shouldBe
          Set(permission1, permission2)
      }
    }

    "removing a user" must {
      "correctly remove all permissions" in withTestData { provider =>
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, ChatPermissionTarget(chat1Id)).get
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user1, ChatPermissionTarget(chat2Id)).get
        provider.permissionsStore.addPermissionsForUser(Set(permission1, permission2), user2, ChatPermissionTarget(chat1Id)).get

        provider.permissionsStore.removeAllPermissionsForUser(user1).get

        provider.permissionsStore.getPermissionsForUser(user1, ChatPermissionTarget(chat1Id)).get shouldBe Set()
        provider.permissionsStore.getPermissionsForUser(user1, ChatPermissionTarget(chat2Id)).get shouldBe Set()

        provider.permissionsStore.getPermissionsForUser(user2, ChatPermissionTarget(chat1Id)).get shouldBe Set(permission1, permission2)

        provider.permissionsStore.getUserPermissionsForTarget(ChatPermissionTarget(chat1Id)).get shouldBe
          Set(UserPermission(user2, permission1), UserPermission(user2, permission2))
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

      provider.chatStore.createChat(
        Some(chat1Id), ChatType.Channel, Instant.now(), ChatMembership.Public, "name", "topic", Some(Set(user1, user2, user3)), user1).get
      provider.chatStore.createChat(
        Some(chat2Id), ChatType.Channel, Instant.now(), ChatMembership.Public, "name", "topic", Some(Set(user1, user2, user3)), user1).get

      testCode(provider)
    }
  }
}
