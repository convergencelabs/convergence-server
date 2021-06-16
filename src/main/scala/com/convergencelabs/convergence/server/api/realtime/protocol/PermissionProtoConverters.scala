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

package com.convergencelabs.convergence.server.api.realtime.protocol

import com.convergencelabs.convergence.proto.core.{AddPermissionsRequestMessage, PermissionsList, RemovePermissionsRequestMessage, SetPermissionsRequestMessage, UserPermissionsEntry}
import com.convergencelabs.convergence.server.api.realtime.protocol.IdentityProtoConverters._
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{GroupPermissions, UserPermissions, WorldPermission}
import com.convergencelabs.convergence.server.backend.services.domain.permissions.{AddPermissions, RemovePermissions, SetPermissions}

/**
 * A collection of helper methods to translate domain objects to and from
 * the protocol buffer message classes for Permissions.
 */
object PermissionProtoConverters {
  
  /**
   * Converts a map of group permissions in a Protocol Buffer representation
   * into a Set of domain GroupPermissions.
   *
   * @param groupPermissionData The Protocol Buffer group permissions map.
   * @return A Set of domain GroupPermission.
   */
  def protoToGroupPermissions(groupPermissionData: Map[String, PermissionsList]): Set[GroupPermissions] = {
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

  /**
   * Converts a Seq of world permissions in a Protocol Buffer representation
   * into a Set of domain WorldPermissions.
   *
   * @param worldPermissions The Protocol Buffer string sequence.
   * @return A Set of domain WorldPermissions.
   */
  def protoToWorldPermissions(worldPermissions: Seq[String]): Set[WorldPermission] = {
    worldPermissions
      .map(p => WorldPermission(p)).toSet
  }

  def userPermissionsToProto(userPermission: Set[UserPermissions]): Seq[UserPermissionsEntry] = {
    userPermission
      .map(p => UserPermissionsEntry(Some(domainUserIdToProto(p.user)), p.permissions.toSeq)).toSeq
  }

  def protoToAddPermissions(message: AddPermissionsRequestMessage): AddPermissions = {
    val AddPermissionsRequestMessage(_, worldPermissionData, userPermissionData, groupPermissionData, _) = message
    val worldPermissions = protoToWorldPermissions(worldPermissionData)
    val userPermissions = protoToUserPermissions(userPermissionData)
    val groupPermissions = protoToGroupPermissions(groupPermissionData)
    AddPermissions(worldPermissions, userPermissions, groupPermissions)
  }

  def protoToRemovePermissions(message: RemovePermissionsRequestMessage): RemovePermissions = {
    val RemovePermissionsRequestMessage(_, worldPermissionData, userPermissionData, groupPermissionData, _) = message
    val worldPermissions = protoToWorldPermissions(worldPermissionData)
    val userPermissions = protoToUserPermissions(userPermissionData)
    val groupPermissions = protoToGroupPermissions(groupPermissionData)
    RemovePermissions(worldPermissions, userPermissions, groupPermissions)
  }

  def protoToSetPermissions(message: SetPermissionsRequestMessage): SetPermissions = {
    val SetPermissionsRequestMessage(_, setWorld, setUser, setGroup, _) = message
    val worldPermissions = setWorld.map(v => protoToWorldPermissions(v.permissions))
    val groupPermissions = setGroup.map(v => PermissionProtoConverters.protoToGroupPermissions(v.permissions))
    val userPermissions = setUser.map(v => PermissionProtoConverters.protoToUserPermissions(v.permissions))
    SetPermissions(worldPermissions, userPermissions, groupPermissions)
  }
}
