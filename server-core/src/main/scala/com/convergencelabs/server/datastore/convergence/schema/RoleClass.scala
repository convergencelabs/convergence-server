package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object RoleClass extends OrientDbClass {
  val ClassName = "Role"
  
  object Fields {
    val Name = "name"
    val Description = "description"
    val Permissions = "permissions"
  }
  
  object Indices {
    val Name = "Role.name"
  }
}
