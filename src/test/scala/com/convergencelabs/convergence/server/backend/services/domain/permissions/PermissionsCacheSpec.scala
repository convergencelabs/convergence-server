/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.permissions

import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{ChatPermissionTarget, PermissionsStore}
import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}
import org.mockito.Mockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import scala.util.Success

class PermissionsCacheSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar {

  private val chatId = "id"
  private val target = ChatPermissionTarget(chatId)
  private val permission1 = "permission1"
  private val permission2 = "permission2"
  private val validPermissions = Set(permission1, permission2)

  private val user1Id = DomainUserId.normal("user1")
  private val user2Id = DomainUserId.normal("user2")
  private val convergenceUser = DomainUserId.convergence("convergenceUser")

  "A PermissionsCache" when {
    "getting permissions for a user" must {

      "return None if no value is present" in {
        val permissionsStore = mock[PermissionsStore]
        Mockito.when(permissionsStore.resolveUserPermissionsForTarget(user1Id, target, validPermissions))
          .thenReturn(Success(Set(permission1)))
        val cache = new PermissionsCache(target, permissionsStore, validPermissions)
        cache.getPermissionsForUser(user1Id).get shouldBe Set(permission1)
      }

    }
  }


}
