/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.datastore.domain.schema

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
