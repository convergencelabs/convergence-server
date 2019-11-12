/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object UserRoleClass extends OrientDbClass {
  val ClassName = "UserRole"
  
  object Fields {
    val User = "user"
    val Role = "role"
    val Target = "target"
  }
  
  object Indices {
    val UserRoleTarget = "UserRole.user_role_target"
  }
}
