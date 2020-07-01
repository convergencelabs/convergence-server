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

import com.convergencelabs.convergence.proto.model.{ModelPermissionsData, UserModelPermissionsData}
import com.convergencelabs.convergence.server.model.domain.model.ModelPermissions
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId

object ModelPermissionConverters {
   def modelPermissionsToProto(permissions: ModelPermissions): ModelPermissionsData =
    ModelPermissionsData(permissions.read, permissions.write, permissions.remove, permissions.manage)

  def modelUserPermissionSeqToMap(entries: Seq[UserModelPermissionsData]): Map[DomainUserId, ModelPermissions] = {
    entries.map { enrty =>
      (
        IdentityProtoConverters.protoToDomainUserId(enrty.user.get),
        ModelPermissions(
          enrty.permissions.get.read,
          enrty.permissions.get.write,
          enrty.permissions.get.remove,
          enrty.permissions.get.manage))
    }.toMap
  }

  def modelUserPermissionSeqToMap(permissionMap: Map[DomainUserId, ModelPermissions]): Seq[UserModelPermissionsData] = {
    val mapped = permissionMap.map {
      case (user, ModelPermissions(read, write, remove, manage)) =>
        (user, UserModelPermissionsData(Some(IdentityProtoConverters.domainUserIdToProto(user)), Some(ModelPermissionsData(read, write, remove, manage))))
    }

    mapped.values.toSeq
  }
}
