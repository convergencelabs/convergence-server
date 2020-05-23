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

package com.convergencelabs.convergence.server.datastore.convergence

import scala.util.Try

import com.convergencelabs.convergence.server.datastore.convergence.UserStore.User
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.util.RandomStringGenerator

import grizzled.slf4j.Logging
import com.convergencelabs.convergence.server.security.Roles

class UserCreator(dbProvider: DatabaseProvider) extends Logging {
  val userStore = new UserStore(dbProvider);
  val namespaceStore = new NamespaceStore(dbProvider);
  val roleStore = new RoleStore(dbProvider);

  val bearerTokenGen = new RandomStringGenerator(32)

  def createUser(user: User, password: String, serverRole: String): Try[Unit] = dbProvider.withDatabase { db =>
    for {
      _ <- userStore.createUser(user, password, bearerTokenGen.nextString)
      _ <- roleStore.setUserRolesForTarget(user.username, ServerRoleTarget(), Set(serverRole))
      namespace <- namespaceStore.createUserNamespace(user.username)
      _ <- roleStore.setUserRolesForTarget(user.username, NamespaceRoleTarget(namespace), Set(Roles.Namespace.Owner))
    } yield {
      ()
    }
  }
}