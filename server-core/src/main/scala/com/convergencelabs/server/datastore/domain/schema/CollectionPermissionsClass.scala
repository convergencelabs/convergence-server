package com.convergencelabs.server.datastore.domain.schema

object CollectionPermissionsClass extends OrientDBClass {
  val ClassName = "CollectionPermissions"

  object Fields {
    val Read = "read"
    val Write = "write"
    val Create = "create"
    val Remove = "remove"
    val Manage = "manage"
  }
}
