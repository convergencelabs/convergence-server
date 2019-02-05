package com.convergencelabs.server.datastore.domain.schema

object CollectionUserPermissionsClass extends OrientDbClass {
  val ClassName = "CollectionUserPermissions"

  object Fields {
    val Collection = "collection"
    val User = "user"
    val Permissions = "permissions"
  }
  
  object Indices {
    val User_Collection = "CollectionUserPermissions.user_collection"
    val User = "CollectionUserPermissions.user"
    val Collection = "CollectionUserPermissions.collection"
  }
}
