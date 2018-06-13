package com.convergencelabs.server.datastore.convergence

import java.time.Duration
import java.time.Instant
import java.util.Date

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.domain.PasswordUtil
import com.convergencelabs.server.util.RandomStringGenerator
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.OElement
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging
import com.convergencelabs.server.db.DatabaseProvider

object UserStore {

  object Fields {
    val Username = "username"
    val Password = "username"
    val Email = "email"
    val FirstName = "firstName"
    val LastName = "lastName"
    val DisplayName = "displayName"
    val ApiKey = "apiKey"
    val Token = "token"
    val ExpireTime = "expireTime"
    val LastLogin = "lastLogin"
  }

  def docToUser(doc: ODocument): User = {
    User(
      doc.getProperty(Fields.Username),
      doc.getProperty(Fields.Email),
      doc.getProperty(Fields.FirstName),
      doc.getProperty(Fields.LastName),
      doc.getProperty(Fields.DisplayName))
  }

  def userToDoc(user: User): ODocument = {
    val doc = new ODocument(Schema.User.Class)
    doc.setProperty(Fields.Username, user.username)
    doc.setProperty(Fields.Email, user.email)
    doc.setProperty(Fields.FirstName, user.firstName)
    doc.setProperty(Fields.LastName, user.lastName)
    doc.setProperty(Fields.DisplayName, user.displayName)
    doc
  }

  def getUserRid(username: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Schema.User.Indices.Username, username)
  }

  case class User(
    username: String,
    email: String,
    firstName: String,
    lastName: String,
    displayName: String)

  sealed trait LoginResult
  case class LoginSuccessful(apiKey: String) extends LoginResult
  case object InvalidCredentials extends LoginResult
  case object NoApiKeyForUser extends LoginResult
}

/**
 * Manages the persistence of Users.  This class manages both user profile records
 * as well as user credentials for users authenticated by Convergence itself.
 *
 * @constructor Creates a new UserStore using the provided connection pool to
 * connect to the database
 *
 * @param dbPool The database pool to use.
 */
class UserStore(
  private[this] val dbProvider: DatabaseProvider,
  private[this] val tokenValidityDuration: Duration)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import UserStore._

  val sessionTokeGenerator = new RandomStringGenerator(32)

  def createUser(user: User, password: String): Try[Unit] = {
    createUserWithPasswordHash(user, PasswordUtil.hashPassword(password))
  }

  def createUserWithPasswordHash(user: User, passwordHash: String): Try[Unit] = tryWithDb { db =>
    val userDoc = UserStore.userToDoc(user)
    db.save(userDoc)
    userDoc.reload()

    val pwDoc: OElement = db.newInstance(Schema.UserCredential.Class)
    pwDoc.setProperty("user", userDoc, OType.LINK)
    pwDoc.setProperty(Fields.Password, passwordHash)

    db.save(pwDoc)
    ()
  } recoverWith (handleDuplicateValue)

  def updateUser(user: User): Try[Unit] = withDb { db =>
    val updatedDoc = UserStore.userToDoc(user)
    OrientDBUtil
      .findDocumentFromSingleValueIndex(db, Schema.User.Indices.Username, user.username)
      .map(_ match {
        case Some(doc) =>
          doc.merge(updatedDoc, false, false)
          db.save(doc)
          ()
        case None =>
          throw EntityNotFoundException()
      }) recoverWith(handleDuplicateValue)
  }

  def deleteUser(username: String): Try[Unit] = tryWithDb { db =>
    OrientDBUtil.mutateOneDocument(
      db,
      "DELETE FROM User WHERE username = :username",
      Map(Fields.Username -> username))
  }

  /**
   * Gets a single user by username.
   *
   * @param username The username of the user to retrieve.
   *
   * @return Some(User) if a user with the specified username exists, or None if no such user exists.
   */
  def getUserByUsername(username: String): Try[Option[User]] = withDb { db =>
    OrientDBUtil.findDocument(
      db,
      "SELECT FROM User WHERE username = :username",
      Map(Fields.Username -> username))
      .map(doc => doc.map(UserStore.docToUser(_)))
  }

  // TODO add an ordering ability.
  def getUsers(filter: Option[String], limit: Option[Int], offset: Option[Int]): Try[List[User]] = withDb { db =>
    val baseQuery = "SELECT FROM User" +
      filter.map(_ => " WHERE username LIKE :searchString ORDER BY username").getOrElse(" ORDER BY username")

    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    val params = filter match {
      case Some(searchFilter) =>
        Map("searchString" -> s"%${searchFilter}%")
      case None =>
        Map().asInstanceOf[Map[String, Any]]
    }

    OrientDBUtil.query(db, query, params).map(docs => docs.map(UserStore.docToUser(_)))
  }

  /**
   * Checks to see if a given username exists in the system.
   *
   * @param username The username to check existence for.
   *
   * @return true if the user exists, false otherwise.
   */
  def userExists(username: String): Try[Boolean] = withDb { db =>
    OrientDBUtil.indexContains(db, Schema.User.Indices.Username, username)
  }

  /**
   * Set the password for an existing user by username.
   *
   * @param username The unique username of the user.
   * @param password The new password to use for internal authentication
   */
  def setUserPassword(username: String, password: String): Try[Unit] = withDb { db =>
    OrientDBUtil.mutateOneDocument(
      db,
      "UPDATE UserCredential SET password = :password WHERE user.username = :username",
      Map(
        Fields.Username -> username,
        "password" -> PasswordUtil.hashPassword(password)))
      .recoverWith {
        case cause: EntityNotFoundException =>
          Failure(new EntityNotFoundException("User not found when setting password."))
      }
  }

  def getUserPasswordHash(username: String): Try[Option[String]] = withDb { db =>
    OrientDBUtil
      .getDocument(db, "SELECT * FROM UserCredential WHERE user.username = :username", Map(Fields.Username -> username))
      .map(doc => Option(doc.getProperty(Fields.Password).asInstanceOf[String]))
  }

  /**
   * Validated that the username and password combination are valid.
   *
   * @param username The username of the user to check the password for.
   * @param password The cleartext password of the user
   *
   * @return true if the username and passowrd match, false otherwise.
   */
  def validateCredentials(username: String, password: String): Try[Option[(String, Instant)]] = withDb { db =>
    val query = new OSQLSynchQuery[ODocument]()
    val params = Map(Fields.Username -> username)

    OrientDBUtil
      .findDocument(
        db,
        "SELECT password, user.username AS username FROM UserCredential WHERE user.username = :username",
        params)
      .map(_ match {
        case Some(doc) =>
          val pwhash: String = doc.getProperty(Fields.Password)
          PasswordUtil.checkPassword(password, pwhash) match {
            case true => {
              val username: String = doc.getProperty(Fields.Username)
              val token = sessionTokeGenerator.nextString()
              val expiration = Instant.now().plus(tokenValidityDuration)
              createToken(username, token, expiration)
              setLastLogin(username, Instant.now())
              Some((token, expiration))
            }
            case false =>
              None
          }
        case None =>
          None
      })
  }

  def login(username: String, password: String): Try[LoginResult] = withDb { db =>
    OrientDBUtil
      .findDocument(
        db,
        "SELECT password, user.apiKey AS apiKey FROM UserCredential WHERE user.username = :username",
        Map(Fields.Username -> username))
      .map {
        _.map { doc =>
          val pwhash: String = doc.getProperty(Fields.Password)
          PasswordUtil.checkPassword(password, pwhash) match {
            case true =>
              Option(doc.getProperty("apiKey").asInstanceOf[String]) map { token =>
                LoginSuccessful(token)
              } getOrElse {
                NoApiKeyForUser
              }
            case false =>
              InvalidCredentials
          }
        } getOrElse (InvalidCredentials)
      }
  }

  def getUserApiKey(username: String): Try[Option[String]] = withDb { db =>
    OrientDBUtil
      .getDocument(
        db,
        "SELECT apiKey FROM User WHERE username = :username",
        Map(Fields.Username -> username))
      .map(doc => Option(doc.field(Fields.ApiKey).asInstanceOf[String]))
  }

  def setUserApiKey(username: String, apiKey: String) = withDb { db =>
    OrientDBUtil.mutateOneDocument(
      db,
      "UPDATE User SET apiKey = :apiKey WHERE username = :username",
      Map(Fields.ApiKey -> apiKey, Fields.Username -> username))
  }

  def clearUserApiKey(username: String) = tryWithDb { db =>
    this.setUserApiKey(username, null).get
  }

  def createToken(username: String, token: String, expiration: Instant): Try[Unit] = withDb { db =>
    val queryStirng =
      """INSERT INTO UserAuthToken SET
        |  user = (SELECT FROM User WHERE username = :username),
        |  token = :token,
        |  expireTime = :expireTime""".stripMargin
    OrientDBUtil
      .mutateOneDocument(
        db,
        queryStirng,
        Map(Fields.Username -> username, Fields.Token -> token, Fields.ExpireTime -> Date.from(expiration)))
      .map(_ => ())
  }

  def removeToken(token: String): Try[Unit] = tryWithDb { db =>
    OrientDBUtil
      .mutateOneDocument(
        db,
        "DELETE FROM UserAuthToken WHERE token = :token",
        Map(Fields.Token -> token))
      .map(_ => ())
  }

  def validateUserSessionToken(token: String): Try[Option[String]] = withDb { db =>
    OrientDBUtil
      .findDocument(
        db,
        "SELECT user.username AS username, expireTime FROM UserAuthToken WHERE token = :token",
        Map(Fields.Token -> token))
      .map(_ match {
        case Some(doc) =>
          val expireTime: Date = doc.getProperty(Fields.ExpireTime)
          val expireInstant: Instant = expireTime.toInstant()
          if (Instant.now().isBefore(expireInstant)) {
            val username: String = doc.getProperty(Fields.Username)
            val newExpiration = Instant.now().plus(tokenValidityDuration)
            updateToken(token, newExpiration)
            Some(username)
          } else {
            None
          }
        case None => None
      })
  }

  def validateUserApiKey(apiKey: String): Try[Option[String]] = withDb { db =>
    OrientDBUtil
      .findDocument(
        db,
        "SELECT username FROM User WHERE apiKey = :apiKey",
        Map(Fields.ApiKey -> apiKey))
      .map(_.map(doc => doc.getProperty(Fields.Username).asInstanceOf[String]))
  }

  def expirationCheck(token: String): Try[Option[(String, Instant)]] = withDb { db =>
    OrientDBUtil
      .findDocument(
        db,
        "SELECT user.username AS username, expireTime FROM UserAuthToken WHERE token = :token",
        Map(Fields.Token -> token))
      .map(_ match {
        case Some(doc) =>
          val expireTime: Date = doc.field(Fields.ExpireTime, OType.DATETIME)
          val expireInstant: Instant = expireTime.toInstant()
          if (Instant.now().isBefore(expireInstant)) {
            val username: String = doc.field(Fields.Username)
            Some((username, expireInstant))
          } else {
            None
          }
        case None => None
      })
  }

  def updateToken(token: String, expiration: Instant): Try[Unit] = withDb { db =>
    OrientDBUtil
      .mutateOneDocument(
        db,
        "UPDATE UserAuthToken SET expireTime = :expireTime WHERE token = :token",
        Map(Fields.Token -> token, Fields.ExpireTime -> Date.from(expiration)))
      .map(_ => ())
  }

  def setLastLogin(username: String, instant: Instant): Try[Unit] = withDb { db =>
    OrientDBUtil
      .findDocumentFromSingleValueIndex(db, Schema.User.Indices.Username, username)
      .map(_.foreach(_.field(Fields.LastLogin, instant.toEpochMilli()).save()))
  }

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Schema.User.Indices.Username =>
          Failure(DuplicateValueException(Fields.Username))
        case Schema.User.Indices.Email =>
          Failure(DuplicateValueException(UserStore.Fields.Email))
        case _ =>
          Failure(e)
      }
  }

}
