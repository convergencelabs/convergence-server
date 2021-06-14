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

import com.convergencelabs.convergence.proto.core.{PermissionsList, UserPermissionsEntry}
import com.convergencelabs.convergence.server.api.realtime.protocol.IdentityProtoConverters._
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{GroupPermissions, UserPermissions}

/**
 * A collection of helper methods to translate domain objects to and from
 * the protocol buffer message classes for Activities.
 */
object ActivityProtoConverters {
  
  /**
   * Converts a map of group permissions in a Protocol Buffer representation
   * into a Set of domain GroupPermissions.
   *
   * @param groupPermissionData The Protocol Buffer group permissions map.
   * @return A Set of domain GroupPermission.
   */
  def protoToAutoCreateOptions(groupPermissionData: Map[String, PermissionsList]): Activity = {
    groupPermissionData.map {
      case (groupId, permissions) => (groupId, GroupPermissions(groupId, permissions.values.toSet))
    }.values.toSet
  }

  /**
   * Converts a Seq of user permissions in a Protocol Buffer representation
   * into a Set of domain UserPermissions.
   *
   * @param userPermissionData The Protocol Buffer group permissions map.
   * @return A Set of domain UserPermissions.
   */
  def protoToUserPermissions(userPermissionData: Seq[UserPermissionsEntry]): Set[UserPermissions] = {
    userPermissionData
      .map(p => permissions.UserPermissions(protoToDomainUserId(p.user.get), p.permissions.toSet)).toSet
  }
}
