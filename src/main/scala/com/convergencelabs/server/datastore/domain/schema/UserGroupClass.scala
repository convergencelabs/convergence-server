package com.convergencelabs.server.datastore.domain.schema

object UserGroupClass extends OrientDbClass {
  val ClassName = "UserGroup"

  object Indices {
    val Id = "UserGroup.id"
  }

  object Fields {
    val Id = "id"
    val Description = "description"
    val Members = "members"
  }
}
