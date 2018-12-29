package com.convergencelabs.server.datastore.domain.schema

object ModelPermissionsClass extends OrientDBClass {
  val ClassName = "ModelPermissions"

  object Fields {
    val Read = "read"
    val Write = "write"
    val Remove = "remove"
    val Manage = "manage"
  }
}
