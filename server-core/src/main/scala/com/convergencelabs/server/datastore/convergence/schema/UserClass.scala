package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object UserClass extends OrientDbClass {
  val ClassName = "User"
  
  object Fields {
    val Username = "username"
    val FirstName = "firstName"
    val LastName = "lastName"
    val DisplayName = "displayName"
    val Email = "email"
    val PasswordHash = "passwordHash"
    val BearerToken = "bearerToken"
    val LastLogin = "lastLogin"
  }
  
  object Indices {
    val Username = "User.username"
    val Email = "User.email"
    val BearerToken = "User.bearerToken"
  }
}
