package com.convergencelabs.server.datastore

import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.{ List => JavaList }
import java.util.UUID

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.convergencelabs.server.User
import com.convergencelabs.server.datastore.domain.PasswordUtil
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import UserStore.Fields.Email
import UserStore.Fields.FirstName
import UserStore.Fields.LastName
import UserStore.Fields.Username
import grizzled.slf4j.Logging
import scala.util.Success

object UserStore {
  val ClassName = "User"

  object Fields {
    val Username = "username"
    val Email = "email"
    val FirstName = "firstName"
    val LastName = "lastName"
    val DisplayName = "displayName"
  }

  def docToUser(doc: ODocument): User = {
    User(
      doc.field(Fields.Username),
      doc.field(Fields.Email),
      doc.field(Fields.FirstName),
      doc.field(Fields.LastName),
      doc.field(Fields.DisplayName))
  }

  def userToDoc(user: User): ODocument = {
    val doc = new ODocument(ClassName)
    doc.field(Fields.Username, user.username)
    doc.field(Fields.Email, user.email)
    doc.field(Fields.FirstName, user.firstName)
    doc.field(Fields.LastName, user.lastName)
    doc.field(Fields.DisplayName, user.displayName)
    doc
  }

  def getUserRid(username: String, db: ODatabaseDocumentTx): Try[ORID] = {
    val query = "SELECT @rid as rid FROM User WHERE username = :username"
    val params = Map("username" -> username)
    QueryUtil.lookupMandatoryDocument(query, params, db) map { _.eval("rid").asInstanceOf[ORID] }
  }
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
  private[this] val dbPool: OPartitionedDatabasePool,
  private[this] val tokenValidityDuration: Duration)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  val Password = "password"
  val Token = "token"
  val ExpireTime = "expireTime"

  val UsernameIndex = "User.username"
  val LastLogin = "lastLogin"

  def createUser(user: User, password: String): Try[CreateResult[Unit]] = {
    createUserWithPasswordHash(user, PasswordUtil.hashPassword(password))
  }

  def createUserWithPasswordHash(user: User, passwordHash: String): Try[CreateResult[Unit]] = tryWithDb { db =>
    val userDoc = UserStore.userToDoc(user)
    db.save(userDoc)
    userDoc.reload()

    val pwDoc = db.newInstance("UserCredential")
    pwDoc.field("user", userDoc, OType.LINK) // FIXME verify this creates a link and now a new doc.
    pwDoc.field(Password, passwordHash)

    db.save(pwDoc)

    CreateSuccess(())
  } recover {
    case e: ORecordDuplicatedException => DuplicateValue
  }

  def deleteUser(username: String): Try[DeleteResult] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM User WHERE username = :username")
    val params = Map(Username -> username)
    val count: Int = db.command(command).execute(params.asJava)
    count match {
      case 0 => NotFound
      case _ => DeleteSuccess
    }
  }

  /**
   * Gets a single user by username.
   *
   * @param username The username of the user to retrieve.
   *
   * @return Some(User) if a user with the specified username exists, or None if no such user exists.
   */
  def getUserByUsername(username: String): Try[Option[User]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE username = :username")
    val params = Map(Username -> username)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(results) { UserStore.docToUser(_) }
  }

  def getUsers(): Try[List[User]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User")
    val results: JavaList[ODocument] = db.command(query).execute()
    results.asScala.toList.map(UserStore.docToUser(_))
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
      case _ => false
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

  def getDomainUserPasswordHash(username: String): Try[Option[String]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT * FROM UserCredential WHERE user.username = :username")
    val params = Map(Username -> username)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.enforceSingletonResultList(result) flatMap { doc => Option(doc.field(Password)) }
  }

  /**
   * Validated that the username and password combination are valid.
   *
   * @param username The username of the user to check the password for.
   * @param password The cleartext password of the user
   *
   * @return true if the username and passowrd match, false otherwise.
   */
  def validateCredentials(username: String, password: String): Try[Option[(String, Instant)]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT password, user.username AS username FROM UserCredential WHERE user.username = :username")
    val params = Map(Username -> username)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        val pwhash: String = doc.field(Password)
        PasswordUtil.checkPassword(password, pwhash) match {
          case true => {
            val username: String = doc.field(Username)
            val token = UUID.randomUUID().toString()
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
    }
  }

  def createToken(username: String, token: String, expiration: Instant): Try[Unit] = tryWithDb { db =>
    val queryStirng =
      """INSERT INTO UserAuthToken SET
        |  user = (SELECT FROM User WHERE username = :username),
        |  token = :token,
        |  expireTime = :expireTime""".stripMargin
    val query = new OCommandSQL(queryStirng)
    val params = Map(Username -> username, Token -> token, ExpireTime -> Date.from(expiration))
    db.command(query).execute(params.asJava)
    Unit
  }

  def validateToken(token: String): Try[Option[String]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT user.username AS username, expireTime FROM UserAuthToken WHERE token = :token")
    val params = Map(Token -> token)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        val expireTime: Date = doc.field(ExpireTime, OType.DATETIME)
        val expireInstant: Instant = expireTime.toInstant()
        if (Instant.now().isBefore(expireInstant)) {
          val username: String = doc.field(Username)
          val newExpiration = Instant.now().plus(tokenValidityDuration)
          updateToken(token, newExpiration)
          Some(username)
        } else {
          None
        }
      case None => None
    }
  }

  def expirationCheck(token: String): Try[Option[(String, Instant)]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT user.username AS username, expireTime FROM UserAuthToken WHERE token = :token")
    val params = Map(Token -> token)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        val expireTime: Date = doc.field(ExpireTime, OType.DATETIME)
        val expireInstant: Instant = expireTime.toInstant()
        if (Instant.now().isBefore(expireInstant)) {
          val username: String = doc.field(Username)
          Some((username, expireInstant))
        } else {
          None
        }
      case None => None
    }
  }

  def updateToken(token: String, expiration: Instant): Try[Unit] = tryWithDb { db =>
    val query = new OCommandSQL("UPDATE UserAuthToken SET expireTime = :expireTime WHERE token = :token")
    val params = Map(Token -> token, ExpireTime -> Date.from(expiration))
    db.command(query).execute(params.asJava)
  }

  def setLastLogin(username: String, instant: Instant): Try[Unit] = tryWithDb { db =>
    val index = db.getMetadata.getIndexManager.getIndex(UsernameIndex)
    if (index.contains(username)) {
      val record: ODocument = index.get(username).asInstanceOf[OIdentifiable].getRecord[ODocument]
      record.field(LastLogin, instant.toEpochMilli()).save()
    }
    Unit
  }
}
