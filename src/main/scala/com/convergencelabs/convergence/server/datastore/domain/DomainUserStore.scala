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

package com.convergencelabs.convergence.server.datastore.domain

import java.lang.{Long => JavaLong}
import java.time.{Duration, Instant}
import java.util.Date

import com.convergencelabs.convergence.server.datastore._
import com.convergencelabs.convergence.server.datastore.domain.schema.DomainSchema
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.{DomainUser, DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.util.RandomStringGenerator
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.index.OCompositeKey
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.OElement
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Try}

object DomainUserStore {

  import schema.UserClass._

  val UserDoesNotExistMessage = "User does not exist"

  case class CreateNormalDomainUser(
                                     username: String,
                                     firstName: Option[String],
                                     lastName: Option[String],
                                     displayName: Option[String],
                                     email: Option[String])

  case class UpdateDomainUser(
                               userId: DomainUserId,
                               firstName: Option[String],
                               lastName: Option[String],
                               displayName: Option[String],
                               email: Option[String],
                               disabled: Option[Boolean])

  private[this] val deletedUsernameGenerator = new RandomStringGenerator(36, RandomStringGenerator.Base64)

  def generateDeletedUsername(): String = {
    deletedUsernameGenerator.nextString()
  }

  def findUserRid(userId: DomainUserId, db: ODatabaseDocument): Try[Option[ORID]] = {
    OrientDBUtil.findIdentityFromSingleValueIndex(db, Indices.UsernameUserType, List(userId.username, userId.userType.toString.toLowerCase))
  }

  def getUserRid(userId: DomainUserId, db: ODatabaseDocument): Try[ORID] = {
    this.getUserRid(userId.username, userId.userType, db)
  }

  def getUserRid(username: String, userType: DomainUserType.Value, db: ODatabaseDocument): Try[ORID] = {
    db.activateOnCurrentThread()
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Indices.UsernameUserType, List(username, userType.toString.toLowerCase))
  }

  def getDomainUsersRids(userIds: List[DomainUserId], db: ODatabaseDocument): Try[List[ORID]] = {
    val keys = userIds.map(userId => new OCompositeKey(List(userId.username, userId.userType.toString.toLowerCase).asJava))
    OrientDBUtil.getIdentitiesFromSingleValueIndex(db, Indices.UsernameUserType, keys)
  }

  def domainUserToDoc(obj: DomainUser): ODocument = {
    val doc = new ODocument(ClassName)
    doc.setProperty(Fields.UserType, obj.userType.toString.toLowerCase)
    doc.setProperty(Fields.Username, obj.username)
    obj.firstName.foreach(doc.setProperty(Fields.FirstName, _))
    obj.lastName.foreach(doc.setProperty(Fields.LastName, _))
    obj.displayName.foreach(doc.setProperty(Fields.DisplayName, _))
    obj.email.foreach(doc.setProperty(Fields.Email, _))
    obj.lastLogin.foreach(doc.setProperty(Fields.LastLogin, _))
    doc.setProperty(Fields.Disabled, obj.disabled)
    doc.setProperty(Fields.Deleted, obj.deleted)
    obj.deletedUsername.foreach(doc.setProperty(Fields.DeletedUsername, _))
    doc
  }

  def docToDomainUser(doc: ODocument): DomainUser = {
    DomainUser(
      DomainUserType.withName(doc.getProperty(Fields.UserType)),
      doc.getProperty(Fields.Username),
      Option(doc.getProperty(Fields.FirstName)),
      Option(doc.getProperty(Fields.LastName)),
      Option(doc.getProperty(Fields.DisplayName)),
      Option(doc.getProperty(Fields.Email)),
      Option(doc.getProperty(Fields.LastLogin)),
      doc.getProperty(Fields.Disabled),
      doc.getProperty(Fields.Deleted),
      Option(doc.getProperty(Fields.DeletedUsername)))
  }

  private val DeleteDomainUserQuery =
    s"""
       |UPDATE
       |  User
       |SET
       |  username = :newUsername,
       |  deleted = true,
       |  deletedUsername = :username
       |WHERE
       |  username = :username AND
       |  userType = '${DomainUserType.Normal.toString.toLowerCase}'
    """.stripMargin
}

/**
 * Manages the persistence of Domain Users.  This class manages both user profile records
 * as well as user credentials for users authenticated by Convergence itself.
 *
 * @constructor Creates a new DomainUserStore using the provided connection pool to
 *              connect to the database
 * @param dbProvider The database pool to use.
 */
class DomainUserStore private[domain](private[this] val dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import DomainUserStore._
  import schema.DomainSchema._
  import schema.UserClass._

  private[this] val Password = "password"

  // TODO make this configurable.
  private[this] val reconnectTokenDuration = Duration.ofHours(24)

  private[this] val reconnectTokenGenerator = new RandomStringGenerator(32)

  /**
   * Creates a new domain user in the system, and optionally set a password.
   * Note the uid as well as the username of the user must be unique among all
   * users in this domain.
   *
   * @param domainUser The user to add to the system.
   * @return A String representing the created users uid.
   */
  def createNormalDomainUser(domainUser: CreateNormalDomainUser): Try[String] = {
    val normalUser = DomainUser(
      DomainUserType.Normal,
      domainUser.username,
      domainUser.firstName,
      domainUser.lastName,
      domainUser.displayName,
      domainUser.email,
      None,
      disabled = false,
      deleted = false,
      None)

    this.createDomainUser(normalUser)
  }

  def createAnonymousDomainUser(displayName: Option[String]): Try[String] = {
    this.nextAnonymousUsername flatMap { username =>
      val anonymousUser = DomainUser(
        DomainUserType.Anonymous,
        username,
        None,
        None,
        displayName,
        None,
        None,
        disabled = false,
        deleted = false,
        None)

      this.createDomainUser(anonymousUser)
    }
  }

  def createAdminDomainUser(convergenceUsername: String): Try[String] = {
    val adminUser = DomainUser(
      DomainUserType.Convergence,
      convergenceUsername,
      None,
      None,
      Some(convergenceUsername),
      None,
      None,
      disabled = false,
      deleted = false,
      None)

    this.createDomainUser(adminUser)
  }

  def createDomainUser(domainUser: DomainUser): Try[String] = tryWithDb { db =>
    val userDoc = DomainUserStore.domainUserToDoc(domainUser)
    db.save(userDoc)

    domainUser.username
  } recoverWith handleDuplicateValue

  /**
   * Deletes a single domain user by username. This is a soft delete that will
   * mark the user as deleted, rename them, and store the original username.
   *
   * @param username the username of the normal user to delete.
   */
  def deleteNormalDomainUser(username: String): Try[Unit] = withDb { db =>
    val newUsername = DomainUserStore.generateDeletedUsername()
    val params = Map(Fields.Username -> username, "newUsername" -> newUsername)
    OrientDBUtil.mutateOneDocument(db, DeleteDomainUserQuery, params)
  }

  /**
   * Updates a DomainUser with new information.  The username of the domain user argument must
   * correspond to an existing user in the database.
   *
   * @param update The user to update.
   */
  def updateDomainUser(update: UpdateDomainUser): Try[Unit] = withDb { db =>
    val UpdateDomainUser(userId, firstName, lastName, displayName, email, disabled) = update

    val query = "SELECT FROM User WHERE username = :username AND userType = :userType"
    val params = Map(Fields.Username -> userId.username, "userType" -> userId.userType.toString.toLowerCase)
    OrientDBUtil.getDocument(db, query, params).map { doc =>
      firstName foreach (doc.setProperty(Fields.FirstName, _))
      lastName foreach (doc.setProperty(Fields.LastName, _))
      displayName foreach (doc.setProperty(Fields.DisplayName, _))
      email foreach (doc.setProperty(Fields.Email, _))
      disabled.foreach(doc.setProperty(Fields.Disabled, _))
      db.save(doc)
      ()
    }
  } recoverWith handleDuplicateValue

  def getNormalDomainUser(username: String): Try[Option[DomainUser]] = {
    getDomainUser(DomainUserId(DomainUserType.Normal, username))
  }

  def getNormalDomainUsers(usernames: List[String]): Try[List[DomainUser]] = {
    getDomainUsers(usernames.map(username => DomainUserId.normal(username)))
  }

  /**
   * Gets a single domain user by username.
   *
   * @param userId The uid of the user to retrieve.
   * @return Some(DomainUser) if a user with the specified username exists, or None if no such user exists.
   */
  def getDomainUser(userId: DomainUserId): Try[Option[DomainUser]] = withDb { db =>
    OrientDBUtil
      .findDocumentFromSingleValueIndex(db, Indices.UsernameUserType, List(userId.username, userId.userType.toString.toLowerCase))
      .map(_.map(DomainUserStore.docToDomainUser))
  }

  def getDomainUsers(userIds: List[DomainUserId]): Try[List[DomainUser]] = withDb { db =>
    val keys = userIds.map(userId => new OCompositeKey(userId.username, userId.userType.toString.toLowerCase))
    OrientDBUtil
      .getDocumentsFromSingleValueIndex(db, Indices.UsernameUserType, keys)
      .map(_.map(docToDomainUser))
  }

  /**
   * Checks to see if a given username exists in the system.
   *
   * @param username The username to check existence for.
   * @return true if the user exists, false otherwise.
   */
  def domainUserExists(username: String): Try[Boolean] = {
    this.userExists(username, DomainUserType.Normal)
  }

  def convergenceUserExists(username: String): Try[Boolean] = {
    this.userExists(username, DomainUserType.Convergence)
  }

  private[this] def userExists(username: String, userType: DomainUserType.Value): Try[Boolean] = withDb { db =>
    val query = "SELECT FROM User WHERE username = :username AND userType = :userType"
    val params = Map(Fields.Username -> username, Fields.UserType -> userType.toString.toLowerCase)
    OrientDBUtil
      .query(db, query, params)
      .map(_.nonEmpty)
  }

  /**
   * Gets a listing of all domain users based on ordering and paging.
   *
   * @param orderBy   The property of the domain user to order by. Defaults to username.
   * @param sortOrder The order (ascending or descending) of the ordering. Defaults to descending.
   * @param limit     maximum number of users to return.  Defaults to unlimited.
   * @param offset    The offset into the ordering to start returning entries.  Defaults to 0.
   */
  def getAllDomainUsers(
                         orderBy: Option[DomainUserField.Field],
                         sortOrder: Option[SortOrder.Value],
                         limit: Option[Int],
                         offset: Option[Int]): Try[List[DomainUser]] = withDb { db =>

    val order = orderBy.getOrElse(DomainUserField.Username)
    val sort = sortOrder.getOrElse(SortOrder.Descending)
    val baseQuery = s"SELECT * FROM User WHERE deleted != true AND userType = 'normal' ORDER BY $order $sort"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    OrientDBUtil
      .query(db, query)
      .map(_.map(DomainUserStore.docToDomainUser))
  }

  def searchUsersByFields(
                           fields: List[DomainUserField.Field],
                           searchString: String,
                           orderBy: Option[DomainUserField.Field],
                           sortOrder: Option[SortOrder.Value],
                           offset: Option[Int],
                           limit: Option[Int]): Try[List[DomainUser]] = withDb { db =>

    val baseQuery = "SELECT * FROM User"
    val whereTerms = ListBuffer[String]()

    fields.foreach { field =>
      whereTerms += s"$field LIKE :searchString"
    }

    val whereClause = " WHERE deleted != true AND userType = 'normal' AND (" + whereTerms.mkString(" OR ") + ")"

    val order = orderBy.getOrElse(DomainUserField.Username)
    val sort = sortOrder.getOrElse(SortOrder.Descending)
    val orderByClause = s" ORDER BY $order $sort"

    val query = OrientDBUtil.buildPagedQuery(baseQuery + whereClause + orderByClause, limit, offset)
    OrientDBUtil
      .query(db, query, Map("searchString" -> s"%$searchString%"))
      .map(_.map(DomainUserStore.docToDomainUser))
  }

  def findUser(search: String,
               exclude: List[DomainUserId],
               offset: Int,
               limit: Int): Try[List[DomainUser]] = withDb { db =>

    // This is a bit hacky, there is a more idiomatic way to do this
    Try {
      var explicitResults = List[DomainUser]()

      if (!exclude.map(_.username).contains(search)) {
        this.getDomainUser(DomainUserId(DomainUserType.Normal, search)).get foreach { user =>
          explicitResults = user :: explicitResults
        }
      }

      explicitResults
    } flatMap { explicitResults =>

      val params = Map[String, Any](
        "search" -> ("%" + search + "%"),
        "exclude" -> exclude.asJava)

      val baseQuery =
        """
          |SELECT *, username.length() as size
          |FROM User
          |WHERE
          |  deleted != true AND
          |  userType = 'normal' AND
          |  username NOT IN :exclude AND
          |  (username LIKE :search OR
          |  email LIKE :search OR
          |  displayName LIKE :search)
          |ORDER BY size ASC, username ASC""".stripMargin

      val query = OrientDBUtil.buildPagedQuery(baseQuery, Some(limit - explicitResults.size), Some(offset))
      OrientDBUtil
        .query(db, query, params)
        .map(_.map(DomainUserStore.docToDomainUser))
        .map(users => users.filterNot(explicitResults.contains(_)))
        .map(explicitResults ::: _)
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
   * @param username     The unique username of the user.
   * @param passwordHash The new password to use for internal authentication
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
        ()
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
   * @return true if the username and password match, false otherwise.
   */
  def validateCredentials(username: String, password: String): Try[Boolean] = withDb { db =>
    val query = "SELECT password FROM UserCredential WHERE user.username = :username AND user.userType = 'normal' AND user.disabled = false"
    val params = Map(Fields.Username -> username)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.exists { doc =>
        Option(doc.getProperty(Password).asInstanceOf[String])
          .exists(PasswordUtil.checkPassword(password, _))
      })
  }

  def createReconnectToken(userId: DomainUserId): Try[String] = withDb { db =>
    OrientDBUtil
      .findIdentityFromSingleValueIndex(db, Indices.UsernameUserType, List(userId.username, userId.userType.toString.toLowerCase))
      .flatMap {

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
            .commandReturningCount(db, command, params)
            .map(_ => token)
        case None =>
          Failure(EntityNotFoundException(DomainUserStore.UserDoesNotExistMessage))
      }
  }

  def removeReconnectToken(token: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM UserReconnectToken WHERE token = :token"
    val params = Map(Classes.UserReconnectToken.Fields.Token -> token)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }

  def validateReconnectToken(token: String): Try[Option[DomainUserId]] = withDb { db =>
    OrientDBUtil
      .findDocumentFromSingleValueIndex(db, Classes.UserReconnectToken.Indices.Token, token)
      .map(_.flatMap { record =>
        val expireTime: Date = record.getProperty(Classes.UserReconnectToken.Fields.ExpireTime)
        val expireInstant: Instant = expireTime.toInstant
        if (Instant.now().isBefore(expireInstant)) {
          val username: String = record.eval("user.username").toString
          val userType: String = record.eval("user.userType").toString
          val userId = DomainUserId(DomainUserType.withName(userType), username)
          val newExpiration = Instant.now().plus(reconnectTokenDuration)
          record.setProperty(Classes.UserReconnectToken.Fields.ExpireTime, Date.from(newExpiration))
          record.save()
          Some(userId)
        } else {
          None
        }
      })
  }

  def setLastLogin(userId: DomainUserId, instant: Instant): Try[Unit] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Indices.UsernameUserType, List(userId.username, userId.userType.toString.toLowerCase))
      .flatMap { record =>
        Try {
          record.setProperty(Fields.LastLogin, Date.from(instant))
          record.save()
          ()
        }
      }
  }

  def getNormalUserCount(): Try[Long] = withDb { db =>
    val query = "SELECT count(*) as count FROM User WHERE userType = 'normal'"
    OrientDBUtil
      .getDocument(db, query)
      .map(_.field("count").asInstanceOf[Long])
  }

  def nextAnonymousUsername(): Try[String] = withDb { db =>
    OrientDBUtil.sequenceNext(db, DomainSchema.Sequences.AnonymousUsername) map (JavaLong.toString(_, 36))
  }

  private[this] def handleDuplicateValue[T]: PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Indices.UsernameUserType =>
          Failure(DuplicateValueException(Fields.Username))
        case _ =>
          Failure(e)
      }
  }
}

object DomainUserField extends Enumeration {
  type Field = Value
  val Username: Field = Value("username")
  val FirstName: Field = Value("firstName")
  val LastName: Field = Value("lastName")
  val DisplayName: Field = Value("displayName")
  val Email: Field = Value("email")
}
