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

import java.time.Instant
import java.util.Date

import com.convergencelabs.convergence.server.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.datastore.convergence.schema.UserSessionTokenClass
import com.convergencelabs.convergence.server.db.DatabaseProvider
import grizzled.slf4j.Logging

import scala.util.Try

object UserSessionTokenStore {
  object Params {
    val User = "user"
    val Username = "username"
    val Token = "token"
    val ExpiresAt = "expiresAt"
  }
}

class UserSessionTokenStore(private[this] val dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import UserSessionTokenStore._

  private[this] val CreateTokenCommand =
    """INSERT INTO UserSessionToken SET
      |  user = (SELECT FROM User WHERE username = :username),
      |  token = :token,
      |  expiresAt = :expiresAt""".stripMargin
  def createToken(username: String, token: String, expiresAt: Instant): Try[Unit] = withDb { db =>
    val params = Map(Params.Username -> username, Params.Token -> token, Params.ExpiresAt -> Date.from(expiresAt))
    OrientDBUtil.commandReturningCount(db, CreateTokenCommand, params).map(_ => ())
  }

  def removeToken(token: String): Try[Unit] = withDb { db =>
    OrientDBUtil.deleteFromSingleValueIndexIfExists(db, UserSessionTokenClass.Indices.Token, token)
  }
  
  private[this] val CleanExpiredTokensCommand = "DELETE FROM UserSessionToken WHERE expiresAt < sysdate()"
  def cleanExpiredTokens(): Try[Unit] = withDb { db =>
    OrientDBUtil.commandReturningCount(db, CleanExpiredTokensCommand).map(_ => ())
  }

  private[this] val ValidateUserSessionToken = "SELECT FROM UserSessionToken WHERE token = :token"
  def validateUserSessionToken(token: String, expiresAt: () => Instant): Try[Option[String]] = withDb { db =>
    val params = Map(Params.Token -> token)
    OrientDBUtil.findDocument(db, ValidateUserSessionToken, params).map {
      case Some(doc) =>
        val expireTime: Date = doc.getProperty(UserSessionTokenClass.Fields.ExpiresAt)
        val expireInstant: Instant = expireTime.toInstant
        if (Instant.now().isBefore(expireInstant)) {
          val username: String = doc.eval("user.username").asInstanceOf[String]
          doc.setProperty(UserSessionTokenClass.Fields.ExpiresAt, Date.from(expiresAt()))
          doc.save()
          Some(username)
        } else {
          None
        }
      case None => None
    }
  }

  private[this] val ExpirationCheckQuery = "SELECT user.username AS username, expiresAt FROM UserSessionToken WHERE token = :token"
  def expirationCheck(token: String): Try[Option[(String, Instant)]] = withDb { db =>
    val params = Map(Params.Token -> token)
    OrientDBUtil.findDocument(db, ExpirationCheckQuery, params).map {
      case Some(doc) =>
        val expireTime: Date = doc.getProperty(UserSessionTokenClass.Fields.ExpiresAt)
        val expireInstant: Instant = expireTime.toInstant
        if (Instant.now().isBefore(expireInstant)) {
          val username: String = doc.field("username")
          Some((username, expireInstant))
        } else {
          None
        }
      case None => None
    }
  }

  private[this] val UpdateTokenCommand = "UPDATE UserSessionToken SET expiresAt = :expiresAt WHERE token = :token"
  def updateToken(token: String, expiration: Instant): Try[Unit] = withDb { db =>
    val params = Map(Params.Token -> token, Params.ExpiresAt -> Date.from(expiration))
    OrientDBUtil.mutateOneDocument(db, UpdateTokenCommand, params)
  }
}
