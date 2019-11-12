/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object UserClass extends OrientDbClass {
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
