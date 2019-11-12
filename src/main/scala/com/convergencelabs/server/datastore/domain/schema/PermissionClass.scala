/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object PermissionClass extends OrientDbClass {
  val ClassName = "Permission"

  object Fields {
    val AssignedTo = "assignedTo"
    val ForRecord = "forRecord"
    val Permission = "permission"
    val Permissions = "permissions"
  }
  
  object Indices {
    val AssignedTo_ForRecord_Permission = "Permission.assignedTo_forRecord_permission"
  }
}
