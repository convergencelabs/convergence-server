package com.convergencelabs.server.datastore

import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.{ List => JavaList }
import java.util.UUID

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

import com.convergencelabs.server.User
import com.convergencelabs.server.datastore.domain.PasswordUtil
import com.convergencelabs.server.datastore.mapper.UserMapper.ODocumentToUser
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import grizzled.slf4j.Logging
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

/**
 * Manages the persistence of Users.  This class manages both user profile records
 * as well as user credentials for users authenticated by Convergence itself.
 *
 * @constructor Creates a new UserStore using the provided connection pool to
 * connect to the database
 *
 * @param dbPool The database pool to use.
 */
class UserStore private[datastore] (
  private[this] val dbPool: OPartitionedDatabasePool,
  private[this] val tokenValidityDuration: Duration)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  val Uid = "uid"
  val Username = "username"
  val Email = "email"
  val FirstName = "firstName"
  val LastName = "lastName"
  val Password = "password"
  val Token = "token"
  val ExpireTime = "expireTime"

  def createUser(user: User, password: String): Try[CreateResult[String]] = tryWithDb { db =>
    // TODO move uid to a sequence in the DB or something

    val userDoc = new ODocument("User");
    userDoc.field(Uid, user.uid);
    userDoc.field(Username, user.username);
    userDoc.field(Email, user.email)
    userDoc.field(FirstName, user.firstName)
    userDoc.field(LastName, user.lastName)

    db.save(userDoc)
    userDoc.reload()

    val pwDoc = db.newInstance("UserCredential")
    pwDoc.field("user", userDoc, OType.LINK) // FIXME verify this creates a link and now a new doc.
    pwDoc.field(Password, PasswordUtil.hashPassword(password))

    db.save(pwDoc)

    val uid: String = userDoc.field(Uid, OType.STRING)
    CreateSuccess(uid)
  } recover {
    case e: ORecordDuplicatedException => DuplicateValue
  }

  /**
   * Gets a single user by uid.
   *
   * @param uid The uid of the user to retrieve.
   *
   * @return Some(User) if a user with the specified uid exists, or None if no such user exists.
   */
  def getUserByUid(uid: String): Try[Option[User]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE uid = :uid")
    val params = Map(Uid -> uid)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(results) { _.asUser }
  }

  /**
   * Gets a single user by username.
   *
   * @param username The uid of the user to retrieve.
   *
   * @return Some(User) if a user with the specified username exists, or None if no such user exists.
   */
  def getUserByUsername(username: String): Try[Option[User]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE username = :username")
    val params = Map(Username -> username)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(results) { _.asUser }
  }

  /**
   * Checks to see if a given username exists in the system.
   *
   * @param username The username to check existence for.
   *
   * @return true if the user exists, false otherwise.
   */
  def userExists(username: String): Try[Boolean] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE username = :username")
    val params = Map(Username -> username)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList match {
      case doc :: Nil => true
      case _          => false
    }
  }

  /**
   * Set the password for an existing user by username.
   *
   * @param username The unique username of the user.
   * @param password The new password to use for internal authentication
   */
  def setUserPassword(username: String, password: String): Try[Unit] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT * FROM UserCredential WHERE user.username = :username")
    val params = Map(Username -> username)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        doc.field(Password, PasswordUtil.hashPassword(password))
        db.save(doc)
        Unit
      case None => throw new IllegalArgumentException("User not found when setting password.")
    }
  }

  /**
   * Validated that the username and password combination are valid.
   *
   * @param username The username of the user to check the password for.
   * @param password The cleartext password of the user
   *
   * @return true if the username and passowrd match, false otherwise.
   */
  def validateCredentials(username: String, password: String): Try[Option[Tuple2[String, String]]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT password, user.uid AS uid FROM UserCredential WHERE user.username = :username")
    val params = Map(Username -> username)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        val pwhash: String = doc.field(Password)
        PasswordUtil.checkPassword(password, pwhash) match {
          case true => {
            val uid: String = doc.field(Uid)
            val token = UUID.randomUUID().toString()
            val expireTime = Date.from(Instant.now().plus(tokenValidityDuration))
            createToken(uid, token, expireTime)
            Some((uid, token))
          }
          case false => None
        }
      case None => None
    }
  }

  def createToken(uid: String, token: String, expireTime: Date): Try[Unit] = tryWithDb { db =>
    val queryStirng =
      """INSERT INTO UserAuthToken SET
        |  user = (SELECT FROM User WHERE uid = :uid),
        |  token = :token,
        |  expireTime = :expireTime""".stripMargin
    val query = new OCommandSQL(queryStirng)
    val params = Map(Uid -> uid, Token -> token, ExpireTime -> expireTime)
    db.command(query).execute(params.asJava)
    Unit
  }

  def validateToken(token: String): Try[Option[String]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT user.uid AS uid, expireTime FROM UserAuthToken WHERE token = :token")
    val params = Map(Token -> token)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        val expireTime: Date = doc.field(ExpireTime, OType.DATETIME)
        if (Instant.now().isBefore(expireTime.toInstant())) {
          val uid: String = doc.field(Uid)
          updateToken(token)
          Some(uid)
        } else {
          None
        }
      case None => None
    }
  }

  def updateToken(token: String): Try[Unit] = tryWithDb { db =>
    val query = new OCommandSQL("UPDATE UserAuthToken SET expireTime = :expireTime WHERE token = :token")
    val params = Map(Token -> token, ExpireTime -> Date.from(Instant.now().plus(tokenValidityDuration)))
    db.command(query).execute(params.asJava)
    Unit
  }
}

object UserField extends Enumeration {
  type Field = Value
  val UserId = Value("uid")
  val Username = Value("username")
}
