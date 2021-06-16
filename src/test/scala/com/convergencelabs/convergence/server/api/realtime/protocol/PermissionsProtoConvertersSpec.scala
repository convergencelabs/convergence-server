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

package com.convergencelabs.convergence.server.api.realtime.protocol

import com.convergencelabs.convergence.proto.core.{DomainUserIdData, DomainUserTypeData, PermissionsList, UserPermissionsEntry}
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{GroupPermissions, UserPermissions}
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PermissionsProtoConvertersSpec extends AnyWordSpec with Matchers {

  private[this] val userId1 = DomainUserId.normal("user1")
  private[this] val userId2 = DomainUserId.convergence("user2")

  private[this] val protoUserId1 = DomainUserIdData(DomainUserTypeData.Normal, userId1.username)
  private[this] val protoUserId2 = DomainUserIdData(DomainUserTypeData.Convergence, userId2.username)

  import PermissionProtoConverters._


  "An PermissionsProtoConverters" when {

    "converting a GroupPermissions from protocol buffers" must {
      "correctly convert group permission data" in {
        val list1 = PermissionsList(Seq("1", "2"))
        val list2 = PermissionsList(Seq("2", "3"))
        val g1 = "g1"
        val g2 = "g2"

        val groupPermissionData: Map[String, PermissionsList] = Map(g1 -> list1, g2 -> list2)
        protoToGroupPermissions(groupPermissionData) shouldBe Set(
          GroupPermissions(g1, list1.values.toSet),
          GroupPermissions(g2, list2.values.toSet),
        )
      }
    }

    "converting a UserPermissions from protocol buffers" must {
      "correctly convert group permission data" in {
        val up1 = UserPermissionsEntry(Some(protoUserId1), Seq("1", "2"))
        val up2 = UserPermissionsEntry(Some(protoUserId2), Seq("2", "3"))

        val userPermissionData: Seq[UserPermissionsEntry] = Seq(up1, up2)
        protoToUserPermissions(userPermissionData) shouldBe Set(
          UserPermissions(userId1, up1.permissions.toSet),
          permissions.UserPermissions(userId2, up2.permissions.toSet)
        )
      }
    }
  }
}
