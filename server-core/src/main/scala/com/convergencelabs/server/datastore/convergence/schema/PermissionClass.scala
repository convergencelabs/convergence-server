package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object PermissionClass extends OrientDbClass {
  val ClassName = "Permission"

  object Fields {
    val Id = "id"
  }

  object Indices {
    val Id = "Permission.id"
  }
}
