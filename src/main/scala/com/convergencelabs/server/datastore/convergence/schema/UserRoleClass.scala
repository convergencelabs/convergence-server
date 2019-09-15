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
