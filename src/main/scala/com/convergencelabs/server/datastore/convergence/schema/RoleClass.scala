/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object RoleClass extends OrientDbClass {
  val ClassName = "Role"
  
  object Fields {
    val Name = "name"
    val TargetClass = "targetClass"
    val Permissions = "permissions"
  }
  
  object Indices {
    val NameTargetClass = "Role.name_targetClass"
  }
}
