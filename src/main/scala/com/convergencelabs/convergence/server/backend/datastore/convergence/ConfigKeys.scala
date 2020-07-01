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

package com.convergencelabs.convergence.server.backend.datastore.convergence

import scala.util.{Failure, Success, Try}

object ConfigKeys {
  object Namespaces {
    val Enabled = "namespaces.enabled"
    val UserNamespacesEnabled = "namespaces.user-namespaces-enabled"
    val DefaultNamespace = "namespaces.default-namespace"
  }

  object Passwords {
    val MinimumLength = "passwords.minimum-length"
    val RequireNumeric = "passwords.require-numeric"
    val RequireLowerCase = "passwords.require-lower-case"
    val RequireUpperCase = "passwords.require-upper-case"
    val RequireSpecialCharacters = "passwords.require-special-characters"
  }

  object Sessions {
    val Timeout = "sessions.timeout"
  }

  private[this] val typeMaps: Map[String, Set[Class[_]]] = Map(
    Namespaces.Enabled -> Set(classOf[Boolean], classOf[java.lang.Boolean]),
    Namespaces.UserNamespacesEnabled -> Set(classOf[Boolean], classOf[java.lang.Boolean]),
    Namespaces.DefaultNamespace -> Set(classOf[String]),

    Passwords.MinimumLength -> Set(classOf[Int], classOf[java.lang.Integer]),
    Passwords.RequireNumeric -> Set(classOf[Boolean], classOf[java.lang.Boolean]),
    Passwords.RequireLowerCase -> Set(classOf[Boolean], classOf[java.lang.Boolean]),
    Passwords.RequireUpperCase -> Set(classOf[Boolean], classOf[java.lang.Boolean]),
    Passwords.RequireSpecialCharacters -> Set(classOf[Boolean], classOf[java.lang.Boolean]),

    Sessions.Timeout ->  Set(classOf[Int], classOf[java.lang.Integer]))

  def validateConfig(key: String, value: Any): Try[Unit] = {
    typeMaps.get(key) match {
      case Some(classes) =>
        if (classes.contains(value.getClass)) {
          Success(())
        } else {
          Failure(new IllegalArgumentException(s"'$key' must be of type ${classes.mkString(", ")} but was of type: ${value.getClass.getName}"))
        }
      case None =>
        Failure(new IllegalArgumentException(s"'$key' is not a valid configuration key."))
    }
  }
}
