package com.convergencelabs.server.datastore.domain.schema

object UserClass extends OrientDBClass {
  val ClassName = "User"

  object Indices {
    val UsernameUserType = "User.username_userType"
  }

   object Fields {
    val UserType = "userType"
    val Username = "username"
    val FirstName = "firstName"
    val LastName = "lastName"
    val DisplayName = "displayName"
    val Email = "email"
    val LastLogin = "lastLogin"
    val Disabled = "disabled"
    val Deleted = "deleted"
    val DeletedUsername = "deletedUsername"
  }
}
