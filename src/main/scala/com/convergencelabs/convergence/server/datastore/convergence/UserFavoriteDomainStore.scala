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

import com.convergencelabs.convergence.server.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.{Domain, DomainId}
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.util.{Success, Try}

object UserFavoriteDomainStore {
  object Params {
    val Username = "username"
    val DomainId = "domainId"
    val NamespaceId = "namespaceId"
  }
}

class UserFavoriteDomainStore(private[this] val dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import UserFavoriteDomainStore._

  private[this] val CreateFavoriteCommand =
    """INSERT INTO UserFavoriteDomain SET
      |  user = (SELECT FROM User WHERE username = :username),
      |  domain = (SELECT FROM Domain WHERE id = :domainId AND namespace.id = :namespaceId)""".stripMargin
  def addFavorite(username: String, domain: DomainId): Try[Unit] = withDb { db =>
    val params = Map(Params.Username -> username, Params.DomainId -> domain.domainId, Params.NamespaceId -> domain.namespace)
    OrientDBUtil.commandReturningCount(db, CreateFavoriteCommand, params)
      .map(_ => ())
      .recoverWith {
        case _: ORecordDuplicatedException =>
          Success(())
      }
  }

  private[this] val GetFavoritesForUser =
    "SELECT expand(domain) FROM UserFavoriteDomain WHERE user.username = :username"
  def getFavoritesForUser(username: String): Try[Set[Domain]] = withDb { db =>
    val params = Map(Params.Username -> username)
    OrientDBUtil.queryAndMap(db, GetFavoritesForUser, params) { doc =>
      DomainStore.docToDomain(doc)
    }.map(_.toSet)
  }

  private[this] val DeleteFavoriteCommand =
    "DELETE FROM UserFavoriteDomain WHERE user.username = :username AND domain.id = :domainId AND domain.namespace.id = :namespaceId"
  def removeFavorite(username: String, domain: DomainId): Try[Unit] = withDb { db =>
    val params = Map(Params.Username -> username, Params.DomainId -> domain.domainId, Params.NamespaceId -> domain.namespace)
    OrientDBUtil.commandReturningCount(db, DeleteFavoriteCommand, params).map(_ => ())
  }

  private[this] val DeleteFavoritesForUserCommand =
    "DELETE FROM UserFavoriteDomain WHERE user.username = :username"
  def removeFavoritesForUser(username: String): Try[Unit] = withDb { db =>
    val params = Map(Params.Username -> username)
    OrientDBUtil.commandReturningCount(db, DeleteFavoritesForUserCommand, params).map(_ => ())
  }

  private[this] val DeleteFavoritesForDomainCommand =
    "DELETE FROM UserFavoriteDomain WHERE domain.id = :domainId AND domain.namespace.id = :namespaceId"
  def removeFavoritesForDomain(domain: DomainId): Try[Unit] = withDb { db =>
    val params = Map(Params.DomainId -> domain.domainId, Params.NamespaceId -> domain.namespace)
    OrientDBUtil.commandReturningCount(db, DeleteFavoritesForDomainCommand, params).map(_ => ())
  }
}
