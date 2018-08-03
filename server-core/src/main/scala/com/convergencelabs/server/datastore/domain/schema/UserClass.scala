package com.convergencelabs.server.datastore.domain.schema

object UserClass extends OrientDBClass {
  val ClassName = "User"

  object Indices {
    val Username = "User.username"
    val Email = "User.email"
  }

   object Fields {
    val UserType = "userType"
    val Username = "username"
    val FirstName = "firstName"
    val LastName = "lastName"
    val DisplayName = "displayName"
    val Email = "email"
    val LastLogin = "lastLogin"
  }
}
