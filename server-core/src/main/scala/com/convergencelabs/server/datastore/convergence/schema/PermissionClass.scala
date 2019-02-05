package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object PermissionClass extends OrientDbClass {
  val ClassName = "Permission"
  
  object Fields {
    val Id = "id"
    val Name = "name"
    val Description = "description"
  }
  
  object Indices {
    val Id = "Permission.id"
    val Name = "Permission.name"
  }
}
