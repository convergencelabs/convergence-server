package com.convergencelabs.server.datastore.domain.schema

object PermissionClass extends OrientDBClass {
  val ClassName = "Permission"

  object Fields {
    val AssignedTo = "assignedTo"
    val ForRecord = "forRecord"
    val Permission = "permission"
    val Permissions = "permissions"
  }
  
  object Indices {
    val AssignedTo_ForRecord_Permission = "Permission.assignedTo_forRecord_permission"
  }
}
