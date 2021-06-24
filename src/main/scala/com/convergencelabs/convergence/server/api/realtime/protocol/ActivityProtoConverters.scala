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

import com.convergencelabs.convergence.proto.activity.ActivityJoinRequestMessage.AutoCreateData
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityAutoCreationOptions

/**
 * A collection of helper methods to translate domain objects to and from
 * the protocol buffer message classes for Activities.
 */
object ActivityProtoConverters {
  
  /**
   * Converts the protocol buffer AutoCreateData into the
   * ActivityAutoCreationOptions object.
   *
   * @param autoCreateData The Protocol Buffer representation of AutoCreateData.
   * @return The converted ActivityAutoCreationOptions
   */
  def protoToAutoCreateOptions(autoCreateData: AutoCreateData): ActivityAutoCreationOptions = {
    val AutoCreateData(ephemeral, world, user, group, _)= autoCreateData

    val worldPermissions = PermissionProtoConverters.protoToWorldPermissions(world)
    val userPermissions = PermissionProtoConverters.protoToUserPermissions(user)
    val groupPermissions = PermissionProtoConverters.protoToGroupPermissions(group)

    ActivityAutoCreationOptions(ephemeral, worldPermissions, userPermissions, groupPermissions)
  }

}
