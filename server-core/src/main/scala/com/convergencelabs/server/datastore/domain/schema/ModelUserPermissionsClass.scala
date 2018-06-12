package com.convergencelabs.server.datastore.domain.schema

object ModelUserPermissionsClass extends OrientDBClass {
  val ClassName = "ModelUserPermissions"

  object Fields {
    val Model = "id"
    val User = "name"
    val Permissions = "permissions"
  }

  object Indices {
    val User_Model = "ModelUserPermissions.user_model"
    val User = "ModelUserPermissions.user"
    val Model = "ModelUserPermissions.model"
  }
}
