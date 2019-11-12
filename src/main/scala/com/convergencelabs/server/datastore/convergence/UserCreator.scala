/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence

import scala.util.Try

import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.util.RandomStringGenerator

import grizzled.slf4j.Logging
import com.convergencelabs.server.security.Roles

class UserCreator(dbProvider: DatabaseProvider) extends Logging {
  val userStore = new UserStore(dbProvider);
  val namespaceStore = new NamespaceStore(dbProvider);
  val roleStore = new RoleStore(dbProvider);

  val bearerTokenGen = new RandomStringGenerator(32)

  def createUser(user: User, password: String, serverRole: String): Try[Unit] = dbProvider.withDatabase { db =>
    for {
      _ <- userStore.createUser(user, password, bearerTokenGen.nextString)
      _ <- roleStore.setUserRolesForTarget(user.username, ServerRoleTarget, Set(serverRole))
      namespace <- namespaceStore.createUserNamespace(user.username)
      _ <- roleStore.setUserRolesForTarget(user.username, NamespaceRoleTarget(namespace), Set(Roles.Namespace.Owner))
    } yield {
      ()
    }
  }
}