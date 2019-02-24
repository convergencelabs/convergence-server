package com.convergencelabs.server.datastore.convergence

import java.time.Instant
import java.util.Date

import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.convergence.schema.UserSessionTokenClass
import com.convergencelabs.server.db.DatabaseProvider

import grizzled.slf4j.Logging

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
    OrientDBUtil.command(db, CreateTokenCommand, params).map(_ => ())
  }

  def removeToken(token: String): Try[Unit] = withDb { db =>
    OrientDBUtil.deleteFromSingleValueIndexIfExists(db, UserSessionTokenClass.Indices.Token, token)
  }
  
  private[this] val CleanExpiredTokensCommand = "DELETE FROM UserSessionToken WHERE expiresAt < sysdate()"
  def cleanExpiredTokens(): Try[Unit] = withDb { db =>
    OrientDBUtil.command(db, CleanExpiredTokensCommand).map(_ => ())
  }

  private[this] val ValidateUserSessionToken = "SELECT FROM UserSessionToken WHERE token = :token"
  def validateUserSessionToken(token: String, expiresAt: () => Instant): Try[Option[String]] = withDb { db =>
    val params = Map(Params.Token -> token)
    OrientDBUtil.findDocument(db, ValidateUserSessionToken, params).map(_ match {
      case Some(doc) =>
        val expireTime: Date = doc.getProperty(UserSessionTokenClass.Fields.ExpiresAt)
        val expireInstant: Instant = expireTime.toInstant()
        if (Instant.now().isBefore(expireInstant)) {
          val username: String = doc.eval("user.username").asInstanceOf[String]
          doc.setProperty(UserSessionTokenClass.Fields.ExpiresAt, Date.from(expiresAt()))
          doc.save()
          Some(username)
        } else {
          None
        }
      case None => None
    })
  }

  private[this] val ExpirationCheckQuery = "SELECT user.username AS username, expiresAt FROM UserSessionToken WHERE token = :token"
  def expirationCheck(token: String): Try[Option[(String, Instant)]] = withDb { db =>
    val params = Map(Params.Token -> token)
    OrientDBUtil.findDocument(db, ExpirationCheckQuery, params).map(_ match {
      case Some(doc) =>
        val expireTime: Date = doc.getProperty(UserSessionTokenClass.Fields.ExpiresAt)
        val expireInstant: Instant = expireTime.toInstant()
        if (Instant.now().isBefore(expireInstant)) {
          val username: String = doc.field("username")
          Some((username, expireInstant))
        } else {
          None
        }
      case None => None
    })
  }

  private[this] val UpdateTokenCommand = "UPDATE UserSessionToken SET expiresAt = :expiresAt WHERE token = :token"
  def updateToken(token: String, expiration: Instant): Try[Unit] = withDb { db =>
    val params = Map(Params.Token -> token, Params.ExpiresAt -> Date.from(expiration))
    OrientDBUtil.mutateOneDocument(db, UpdateTokenCommand, params)
  }
}
