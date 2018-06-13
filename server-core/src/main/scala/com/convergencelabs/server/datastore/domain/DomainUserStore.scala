package com.convergencelabs.server.datastore.domain

import java.lang.{ Long => JavaLong }
import java.time.Duration
import java.time.Instant
import java.util.Date

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable.ListBuffer
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.SortOrder
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateNormalDomainUser
import com.convergencelabs.server.datastore.domain.DomainUserStore.UpdateDomainUser
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.util.RandomStringGenerator
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.OElement
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException



import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.OrientDBUtil

object DomainUserStore {
  import schema.UserClass._

  val AdminUserPrefeix = "admin:"
  val AnonymousUserPrefeix = "anonymous:"

  val UserDoesNotExistMessage = "User does not exist"

  case class CreateNormalDomainUser(
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    displayName: Option[String],
    email: Option[String])

  case class UpdateDomainUser(
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    displayName: Option[String],
    email: Option[String])

  def adminUsername(convergenceUsername: String): String = {
    AdminUserPrefeix + convergenceUsername
  }

  def anonymousUsername(username: String): String = {
    AnonymousUserPrefeix + username
  }

  def getUserRid(username: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Indices.Username, username)
  }

  def domainUserToDoc(obj: DomainUser): ODocument = {
    val doc = new ODocument(ClassName)
    doc.setProperty(Fields.UserType, obj.userType.toString.toLowerCase)
    doc.setProperty(Fields.Username, obj.username)
    obj.firstName.map(doc.setProperty(Fields.FirstName, _))
    obj.lastName.map(doc.setProperty(Fields.LastName, _))
    obj.displayName.map(doc.setProperty(Fields.DisplayName, _))
    obj.email.map(doc.setProperty(Fields.Email, _))
    doc
  }

  def docToDomainUser(doc: ODocument): DomainUser = {
    DomainUser(
      DomainUserType.withNameOpt(doc.getProperty(Fields.UserType)).get,
      doc.getProperty(Fields.Username),
      Option(doc.getProperty(Fields.FirstName)),
      Option(doc.getProperty(Fields.LastName)),
      Option(doc.getProperty(Fields.DisplayName)),
      Option(doc.getProperty(Fields.Email)))
  }
}

// FIXME there is some odd things in this class around how we search for things using
// normal / non normal users. It's not consistent. For example could a non-normal
// user have the same email as a normal one. Are we assuming user names are alway
// unique because we prefix the ones that are not? etc/

/**
 * Manages the persistence of Domain Users.  This class manages both user profile records
 * as well as user credentials for users authenticated by Convergence itself.
 *
 * @constructor Creates a new DomainUserStore using the provided connection pool to
 * connect to the database
 *
 * @param dbPool The database pool to use.
 */
class DomainUserStore private[domain] (private[this] val dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {
  
  import schema.UserClass._
  import schema.DomainSchema._

  val Password = "password"

  // TODO make this configurable.
  val reconnectTokenDuration = Duration.ofHours(24)

  val reconnectTokenGenerator = new RandomStringGenerator(32)

  /**
   * Creates a new domain user in the system, and optionally set a password.
   * Note the uid as well as the username of the user must be unique among all
   * users in this domain.
   *
   * @param domainUser The user to add to the system.
   *
   * @return A String representing the created users uid.
   */
  def createNormalDomainUser(domainUser: CreateNormalDomainUser): Try[String] = {
    // fixme disallow special chars, specifically : in the username
    val normalUser = DomainUser(
      DomainUserType.Normal,
      domainUser.username,
      domainUser.firstName,
      domainUser.lastName,
      domainUser.displayName,
      domainUser.email)

    this.createDomainUser(normalUser)
  }

  def createAnonymousDomainUser(displayName: Option[String]): Try[String] = {
    this.nextAnonymousUsername flatMap { next =>
      val username = "anonymous:" + next
      val anonymousUser = DomainUser(
        DomainUserType.Anonymous,
        username,
        None,
        None,
        displayName,
        None)

      this.createDomainUser(anonymousUser)
    }
  }

  def createAdminDomainUser(convergenceUsername: String): Try[String] = {
    val username = "admin:" + convergenceUsername
    val adminUser = DomainUser(
      DomainUserType.Admin,
      username,
      None,
      None,
      Some(convergenceUsername + "(Admin)"),
      None)

    this.createDomainUser(adminUser)
  }

  def createDomainUser(domainUser: DomainUser): Try[String] = tryWithDb { db =>
    val create = DomainUser(
      domainUser.userType,
      domainUser.username,
      domainUser.firstName,
      domainUser.lastName,
      domainUser.displayName,
      domainUser.email)

    val userDoc = DomainUserStore.domainUserToDoc(create)
    db.save(userDoc)

    domainUser.username
  } recoverWith (handleDuplicateValue)

  /**
   * Deletes a single domain user by uid.
   *
   * @param the uid of the user to delete.
   */
  def deleteDomainUser(username: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM User WHERE username = :username AND userType = 'normal'"
    val params = Map(Fields.Username -> username)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }

  /**
   * Updates a DomainUser with new information.  The uid of the domain user argument must
   * correspond to an existing user in the database.
   *
   * @param domainUser The user to update.
   */
  def updateDomainUser(update: UpdateDomainUser): Try[Unit] = withDb { db =>
    val UpdateDomainUser(username, firstName, lastName, displayName, email) = update;
    val domainUser = DomainUser(DomainUserType.Normal, username, firstName, lastName, displayName, email)

    val query = "SELECT FROM User WHERE username = :username AND userType = 'normal'"
    val params = Map(Fields.Username -> domainUser.username)
    OrientDBUtil.getDocument(db, query, params).map { doc =>
      val updatedDoc = DomainUserStore.domainUserToDoc(domainUser)
      doc.merge(updatedDoc, false, false)
      db.save(doc)
    }
  } recoverWith (handleDuplicateValue)

  /**
   * Gets a single domain user by username.
   *
   * @param username The uid of the user to retrieve.
   *
   * @return Some(DomainUser) if a user with the specified username exists, or None if no such user exists.
   */
  def getDomainUserByUsername(username: String): Try[Option[DomainUser]] = withDb { db =>
    OrientDBUtil
      .findDocumentFromSingleValueIndex(db, Indices.Username, username)
      .map(_.map(DomainUserStore.docToDomainUser(_)))
  }

  /**
   * Gets a list of domain users matching any of a list of usernames.
   *
   * @param uids The list of usernames of the users to retrieve.
   *
   * @return A list of users matching the list of supplied usernames.
   */
  def getDomainUsersByUsername(usernames: List[String]): Try[List[DomainUser]] = withDb { db =>
    val query = "SELECT FROM User WHERE username in :usernames"
    val params = Map("usernames" -> usernames.asJava)
    OrientDBUtil
      .query(db, query, params)
      .map(_.map(DomainUserStore.docToDomainUser(_)))
  }

  /**
   * Gets a single domain user by email.
   *
   * @param email The email of the user to retrieve.
   *
   * @return Some(DomainUser) if a user with the specified email exists, or None if no such user exists.
   */
  def getDomainUserByEmail(email: String): Try[Option[DomainUser]] = withDb { db =>
    // FIXME do we need to check for normal here?
    val query = "SELECT FROM User WHERE email = :email AND userType = 'normal'"
    val params = Map("email" -> email)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(DomainUserStore.docToDomainUser(_)))
  }

  /**
   * Gets a list of domain users matching any of a list of emails.
   *
   * @param uids The list of emails of the users to retrieve.
   *
   * @return A list of users matching the list of supplied emails.
   */
  def getDomainUsersByEmail(emails: List[String]): Try[List[DomainUser]] = withDb { db =>
    val query = "SELECT FROM User WHERE email in :emails AND userType = 'normal'"
    val params = Map("emails" -> emails.asJava)
    OrientDBUtil
      .query(db, query, params)
      .map(_.map(DomainUserStore.docToDomainUser(_)))
  }

  /**
   * Checks to see if a given username exists in the system.
   *
   * @param username The username to check existence for.
   *
   * @return true if the user exists, false otherwise.
   */
  def domainUserExists(username: String): Try[Boolean] = {
    this.userExists(username, DomainUserType.Normal)
  }

  def adminUserExists(username: String): Try[Boolean] = {
    this.userExists(DomainUserStore.adminUsername(username), DomainUserType.Admin)
  }

  private[this] def userExists(username: String, userType: DomainUserType.Value): Try[Boolean] = withDb { db =>
    val query = "SELECT FROM User WHERE username = :username AND userType = :userType"
    val params = Map(Fields.Username -> username, Fields.UserType -> userType.toString.toLowerCase)
    OrientDBUtil
      .query(db, query, params)
      .map(!_.isEmpty)
  }

  /**
   * Gets a listing of all domain users based on ordering and paging.
   *
   * @param orderBy The property of the domain user to order by. Defaults to username.
   * @param sortOrder The order (ascending or descending) of the ordering. Defaults to descending.
   * @param limit maximum number of users to return.  Defaults to unlimited.
   * @param offset The offset into the ordering to start returning entries.  Defaults to 0.
   */
  def getAllDomainUsers(
    orderBy: Option[DomainUserField.Field],
    sortOrder: Option[SortOrder.Value],
    limit: Option[Int],
    offset: Option[Int]): Try[List[DomainUser]] = withDb { db =>

    val order = orderBy.getOrElse(DomainUserField.Username)
    val sort = sortOrder.getOrElse(SortOrder.Descending)
    val baseQuery = s"SELECT * FROM User WHERE userType = 'normal' ORDER BY $order $sort"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    OrientDBUtil
      .query(db, query)
      .map(_.map(DomainUserStore.docToDomainUser(_)))
  }

  def searchUsersByFields(
    fields: List[DomainUserField.Field],
    searchString: String,
    orderBy: Option[DomainUserField.Field],
    sortOrder: Option[SortOrder.Value],
    limit: Option[Int],
    offset: Option[Int]): Try[List[DomainUser]] = withDb { db =>

    val baseQuery = "SELECT * FROM User"
    val whereTerms = ListBuffer[String]()

    fields.foreach { field =>
      whereTerms += s"$field LIKE :searchString"
    }

    val whereClause = " WHERE userType = 'normal' AND (" + whereTerms.mkString(" OR ") + ")"

    val order = orderBy.getOrElse(DomainUserField.Username)
    val sort = sortOrder.getOrElse(SortOrder.Descending)
    val orderByClause = s" ORDER BY $order $sort"

    val query = OrientDBUtil.buildPagedQuery(baseQuery + whereClause + orderByClause, limit, offset)
    OrientDBUtil
      .query(db, query)
      .map(_.map(DomainUserStore.docToDomainUser(_)))
  }

  def findUser(
    search: String,
    exclude: List[String],
    offset: Int,
    limit: Int): Try[List[DomainUser]] = withDb { db =>

    // This is a bit hacky, there is a more idiomatic way to do this
    Try {
      var excplicitResults = List[DomainUser]()

      if (!exclude.contains(search)) {
        this.getDomainUserByUsername(search).get foreach { user =>
          excplicitResults = user :: excplicitResults
        }

        this.getDomainUserByEmail(search).get
          .filter(!excplicitResults.contains(_))
          .foreach { user =>
            excplicitResults = user :: excplicitResults
          }
      }

      excplicitResults
    } flatMap { excplicitResults =>

      val params = scala.collection.mutable.Map[String, Any](
        "search" -> ("%" + search + "%"),
        "exclude" -> exclude.asJava)

      val baseQuery = """
      |SELECT *, username.length() as size
      |FROM User
      |WHERE
      |  userType = 'normal' AND
      |  username NOT IN :exclude AND
      |  (username LIKE :search OR 
      |  email LIKE :search OR
      |  displayName LIKE :search)
      |ORDER BY size ASC, username ASC""".stripMargin

      val query = OrientDBUtil.buildPagedQuery(baseQuery, Some(limit - excplicitResults.size), Some(offset))
      OrientDBUtil
        .query(db, query)
        .map(_.map(DomainUserStore.docToDomainUser(_)))
        .filter(!excplicitResults.contains(_))
        .map(excplicitResults ::: _)
    }
  }

  /**
   * Set the password for an existing user by uid.
   *
   * @param username The unique username of the user.
   * @param password The new password to use for internal authentication
   */
  def setDomainUserPassword(username: String, password: String): Try[Unit] = {
    setDomainUserPasswordHash(username, PasswordUtil.hashPassword(password))
  }

  /**
   * Set the password for an existing user by uid.
   *
   * @param username The unique username of the user.
   * @param password The new password to use for internal authentication
   */
  def setDomainUserPasswordHash(username: String, passwordHash: String): Try[Unit] = withDb { db =>
    // FIXME use index.
    val query = "SELECT @rid as rid FROM User WHERE username = :username AND userType = 'normal'"
    val params = Map(Fields.Username -> username)
    OrientDBUtil
      .getDocument(db, query, params)
      .flatMap { ridDoc =>
        val rid = ridDoc.field("rid").asInstanceOf[ODocument].getIdentity
        val query = "SELECT * FROM UserCredential WHERE user = :user"
        val params = Map("user" -> rid)
        OrientDBUtil.findDocument(db, query, params).map(_.getOrElse {
          val newDoc: OElement = db.newInstance("UserCredential")
          newDoc.setProperty("user", rid, OType.LINK)
          newDoc
        })
      } flatMap { doc =>
        Try {
          doc.setProperty(Password, passwordHash)
          db.save(doc)
        }
      }
  }

  def getDomainUserPasswordHash(username: String): Try[Option[String]] = withDb { db =>
    val query = "SELECT * FROM UserCredential WHERE user.username = :username AND user.userType = 'normal'"
    val params = Map(Fields.Username -> username)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.flatMap(doc => Option(doc.getProperty(Password))))
  }

  /**
   * Validated that the username and password combination are valid.
   *
   * @param username The username of the user to check the password for.
   * @param password The cleartext password of the user
   *
   * @return true if the username and password match, false otherwise.
   */
  def validateCredentials(username: String, password: String): Try[Boolean] = withDb { db =>
    val query = "SELECT password FROM UserCredential WHERE user.username = :username AND user.userType = 'normal'"
    val params = Map(Fields.Username -> username)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map { doc =>
        Option(doc.getProperty(Password).asInstanceOf[String])
          .map(PasswordUtil.checkPassword(password, _))
          .getOrElse(false)
      }.getOrElse(false))
  }

  def createReconnectToken(username: String): Try[String] = withDb { db =>
    OrientDBUtil
      .findIdentityFromSingleValueIndex(db, Indices.Username, username)
      .flatMap {
        _ match {
          case Some(userORID) =>
            val expiration = Instant.now().plus(reconnectTokenDuration)
            val token = reconnectTokenGenerator.nextString()
            val command =
              """INSERT INTO UserReconnectToken SET
                |  user = :user,
                |  token = :token,
                |  expireTime = :expireTime""".stripMargin
            val params = Map(
                Classes.UserReconnectToken.Fields.User -> userORID, 
                Classes.UserReconnectToken.Fields.Token -> token, 
                Classes.UserReconnectToken.Fields.ExpireTime -> Date.from(expiration))
            OrientDBUtil
              .mutateOneDocument(db, command, params)
              .map(_ => token)
          case None =>
            Failure(new EntityNotFoundException(DomainUserStore.UserDoesNotExistMessage))
        }
      }
  }

  def removeReconnectToken(token: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM UserReconnectToken WHERE token = :token"
    val params = Map(Classes.UserReconnectToken.Fields.Token  -> token)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }

  def validateReconnectToken(token: String): Try[Option[String]] = withDb { db =>
    OrientDBUtil
      .findDocumentFromSingleValueIndex(db, Classes.UserReconnectToken.Indices.Token, token)
      .map(_.flatMap { record =>
        val expireTime: Date = record.getProperty(Classes.UserReconnectToken.Fields.ExpireTime)
        val expireInstant: Instant = expireTime.toInstant()
        if (Instant.now().isBefore(expireInstant)) {
          val username: String = record.getProperty("user.username")
          val newExpiration = Instant.now().plus(reconnectTokenDuration)
          record.setProperty(Classes.UserReconnectToken.Fields.ExpireTime, Date.from(newExpiration))
          record.save()
          Some(username)
        } else {
          None
        }
      })
  }

  def setLastLogin(username: String, userType: DomainUserType.Value, instant: Instant): Try[Unit] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Indices.Username, username)
      .flatMap { record =>
        Try {
          record.setProperty(Fields.LastLogin, Date.from(instant))
          record.save()
          ()
        }
      }
  }

  def getNormalUserCount(): Try[Long] = withDb { db =>
    val query = "SELECT count(username) as count FROM User WHERE userType = 'normal'"
    OrientDBUtil
      .getDocument(db, query)
      .map(_.field("count").asInstanceOf[Long])
  }

  def nextAnonymousUsername: Try[String] = withDb { db =>
    // FIXME this does not seem to work.
    //val seq = db.getMetadata().getSequenceLibrary().getSequence(AnonymousUsernameSeq)
    //val next = seq.next()
    val query = "SELECT sequence('anonymousUsernameSeq').next() as next"
    OrientDBUtil
      .getDocument(db, query)
      .map(_.field("next").asInstanceOf[Long])
      .map(JavaLong.toString(_, 36))
  }

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Indices.Username =>
          Failure(DuplicateValueException(Fields.Username))
        case Indices.Email =>
          Failure(DuplicateValueException(Fields.Email))
        case _ =>
          Failure(e)
      }
  }
}

object DomainUserField extends Enumeration {
  type Field = Value
  val Username = Value("username")
  val FirstName = Value("firstName")
  val LastName = Value("lastName")
  val DisplayName = Value("displayName")
  val Email = Value("email")
}
