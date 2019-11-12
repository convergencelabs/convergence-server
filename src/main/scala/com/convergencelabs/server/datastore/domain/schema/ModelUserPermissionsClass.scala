/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

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
