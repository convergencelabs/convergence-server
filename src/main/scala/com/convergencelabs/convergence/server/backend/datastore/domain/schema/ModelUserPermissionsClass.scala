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

package com.convergencelabs.convergence.server.backend.datastore.domain.schema

object ModelUserPermissionsClass extends OrientDbClass {
  val ClassName = "ModelUserPermissions"

  object Fields {
    val Model = "model"
    val User = "user"
    val Permissions = "permissions"
  }

  object Indices {
    val User_Model = "ModelUserPermissions.user_model"
    val User = "ModelUserPermissions.user"
    val Model = "ModelUserPermissions.model"
  }
}
