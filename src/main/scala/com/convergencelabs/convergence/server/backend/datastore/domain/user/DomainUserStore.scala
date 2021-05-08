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

package com.convergencelabs.convergence.server.backend.datastore.domain.user

import java.lang.{Long => JavaLong}
import java.time.{Duration, Instant}
import java.util.Date

import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.datastore._
import com.convergencelabs.convergence.server.backend.datastore.domain.schema
import com.convergencelabs.convergence.server.backend.datastore.domain.schema.{DomainSchema, UserCredentialClass}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.security.PasswordUtil
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset, RandomStringGenerator}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.index.OCompositeKey
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.OElement
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

/**
 * Manages the persistence of Domain Users.  This class manages both user profile records
 * as well as user credentials for users authenticated by Convergence itself.
 *
 * @constructor Creates a new DomainUserStore using the provided connection pool to
 *              connect to the database
 * @param dbProvider The database pool to use.
 */
class DomainUserStore private[domain](dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import DomainUserStore._
  import schema.DomainSchema._
  import schema.UserClass._


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
    this.nextAnonymousUsername() flatMap { username =>
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

  private[this] val DeleteDomainUserQuery =
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

  /**
   * Gets a list of domain users based on user ids.
   *
   * @param userIds The user ids of the users to get.
   * @return The set of users corresponding to the ids.
   */
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
   * @param offset    The offset into the ordering to start returning entries.  Defaults to 0.
   * @param limit     maximum number of users to return.  Defaults to unlimited.
   */
  def getAllDomainUsers(orderBy: Option[DomainUserField.Field],
                        sortOrder: Option[SortOrder.Value],
                        offset: QueryOffset,
                        limit: QueryLimit): Try[PagedData[DomainUser]] = withDb { db =>
    val countQuery = s"SELECT count(*) as count FROM User WHERE deleted != true AND userType = 'normal'"

    val order = orderBy.getOrElse(DomainUserField.Username)
    val sort = sortOrder.getOrElse(SortOrder.Descending)

    val baseQuery = s"SELECT * FROM User WHERE deleted != true AND userType = 'normal' ORDER BY $order $sort"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)

    for {
      count <- OrientDBUtil
        .getDocument(db, countQuery)
        .map(_.field("count").asInstanceOf[Long])
      users <- OrientDBUtil
        .query(db, query)
        .map(_.map(DomainUserStore.docToDomainUser))
    } yield {
      PagedData(users, offset.getOrZero, count)
    }
  }

  /**
   * Searches domain users.
   *
   * @param searchFields The fields in the user to include in the search.
   * @param searchTerm   The search term to use. This term will essentially
   *                     be a wildcard search.
   * @param orderBy      What field to order the results by.
   * @param sortOrder    Given the sort field, what order to sort.
   * @param offset       The offset of paging.
   * @param limit        The maximum number of results for each page.
   * @return The page of data.
   */
  def searchUsersByFields(searchFields: List[DomainUserField.Field],
                          searchTerm: String,
                          orderBy: Option[DomainUserField.Field],
                          sortOrder: Option[SortOrder.Value],
                          offset: QueryOffset,
                          limit: QueryLimit): Try[PagedData[DomainUser]] = withDb { db =>

    val baseQuery = "SELECT * FROM User"
    val whereTerms = ListBuffer[String]()

    searchFields.foreach { field =>
      whereTerms += s"$field LIKE :searchString"
    }

    val whereClause = " WHERE deleted != true AND userType = 'normal' AND (" + whereTerms.mkString(" OR ") + ")"

    val order = orderBy.getOrElse(DomainUserField.Username)
    val sort = sortOrder.getOrElse(SortOrder.Descending)
    val orderByClause = s" ORDER BY $order $sort"

    val query = OrientDBUtil.buildPagedQuery(baseQuery + whereClause + orderByClause, limit, offset)
    val countQuery = "SELECT count(*) as count FROM User" + whereClause

    for {
      count <- OrientDBUtil
        .getDocument(db, countQuery)
        .map(_.field("count").asInstanceOf[Long])
      users <- OrientDBUtil
        .query(db, query, Map("searchString" -> s"%$searchTerm%"))
        .map(_.map(DomainUserStore.docToDomainUser))
    } yield {
      PagedData(users, offset.getOrZero, count)
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
    for {
      userRid <- DomainUserStore.getUserRid(username, DomainUserType.Normal, db)
      credentialDoc <- getOrCreateUserCredentialDoc(userRid, db)
      _ <- Try {
        credentialDoc.setProperty(UserCredentialClass.Fields.Password, passwordHash)
        db.save(credentialDoc)
        ()
      }
    } yield ()
  }

  private[this] def getOrCreateUserCredentialDoc(userRid: ORID, db: ODatabaseDocument): Try[OElement] = {
    val query = "SELECT * FROM UserCredential WHERE user = :user"
    val params = Map("user" -> userRid)
    OrientDBUtil.findDocument(db, query, params).map(_.getOrElse {
      val newDoc: OElement = db.newInstance("UserCredential")
      newDoc.setProperty(UserCredentialClass.Fields.User, userRid, OType.LINK)
      newDoc
    })
  }

  /**
   * Gets the password hash for a normal domain user. Normal domain users are
   * the only ones that can have passwords.
   *
   * @param username The username of the domain user to get the password hash
   *                 for.
   * @return The password hash, or None if this domain user does not currently
   *         have  password set.
   */
  def getNormalUserPasswordHash(username: String): Try[Option[String]] = withDb { db =>
    val params = Map(Fields.Username -> username)
    OrientDBUtil
      .findDocument(db, GetDomainUserPasswordHash, params)
      .map(_.flatMap(doc => Option(doc.getProperty(UserCredentialClass.Fields.Password))))
  }

  private[this] val GetDomainUserPasswordHash =
    "SELECT * FROM UserCredential WHERE user.username = :username AND user.userType = 'normal'"

  /**
   * Validates that the username and password combination are valid for a
   * given normal domain user.
   *
   * @param username The username of the user to check the password for.
   * @param password The cleartext password of the user
   * @return true if the username and password match, false otherwise.
   */
  def validateNormalUserCredentials(username: String, password: String): Try[Boolean] = withDb { db =>
    val params = Map(Fields.Username -> username)
    OrientDBUtil
      .findDocument(db, ValidateCredentialsQuery, params)
      .map(_.exists { doc =>
        Option(doc.getProperty(UserCredentialClass.Fields.Password).asInstanceOf[String])
          .exists(PasswordUtil.checkPassword(password, _))
      })
  }

  private[this] val ValidateCredentialsQuery =
    "SELECT password FROM UserCredential WHERE user.username = :username AND user.userType = 'normal' AND user.disabled = false"

  /**
   * Creates a reconnect token for a domain user.
   *
   * @param userId        The id of the domain user to create the token for.
   * @param tokenDuration How long the token should be valid for.
   * @return The generated token.
   */
  def createReconnectToken(userId: DomainUserId, tokenDuration: Duration): Try[String] = withDb { db =>
    OrientDBUtil
      .findIdentityFromSingleValueIndex(db, Indices.UsernameUserType, List(userId.username, userId.userType.toString.toLowerCase))
      .flatMap {
        case Some(userORID) =>
          val expiration = Instant.now().plus(tokenDuration)
          val token = ReconnectTokenGenerator.nextString()
          val params = Map(
            Classes.UserReconnectToken.Fields.User -> userORID,
            Classes.UserReconnectToken.Fields.Token -> token,
            Classes.UserReconnectToken.Fields.ExpireTime -> Date.from(expiration))
          OrientDBUtil
            .commandReturningCount(db, CreateReconnectTokenCommand, params)
            .map(_ => token)
        case None =>
          Failure(EntityNotFoundException(DomainUserStore.UserDoesNotExistMessage))
      }
  }

  private[this] val CreateReconnectTokenCommand =
    """INSERT INTO UserReconnectToken SET
      |  user = :user,
      |  token = :token,
      |  expireTime = :expireTime""".stripMargin

  /**
   * Removes a reconnect token from the database.
   *
   * @param token The token to remove.
   * @return Success if the operation succeeds.
   */
  def removeReconnectToken(token: String): Try[Unit] = withDb { db =>
    val params = Map(Classes.UserReconnectToken.Fields.Token -> token)
    OrientDBUtil.mutateOneDocument(db, RemoveReconnectTokenCommand, params)
  }

  val RemoveReconnectTokenCommand =
    "DELETE FROM UserReconnectToken WHERE token = :token"

  /**
   * Validates a reconnect token exists and returns the user id of the user the
   * token corresponds to. This method also updates the expiration time of the
   * token if the token is valid, to extend the validity period.
   *
   * @param token         The token to validate / refresh.
   * @param tokenDuration The duration by which to extend the validity of the
   *                      token, relative to now.
   * @return The user id of the user if validation succeeds or None, if a valid
   *         token can not be found.
   */
  def validateAndRefreshReconnectToken(token: String, tokenDuration: Duration): Try[Option[DomainUserId]] = withDb { db =>
    OrientDBUtil
      .findDocumentFromSingleValueIndex(db, Classes.UserReconnectToken.Indices.Token, token)
      .map(_.flatMap { record =>
        val expireTime: Date = record.getProperty(Classes.UserReconnectToken.Fields.ExpireTime)
        val expireInstant: Instant = expireTime.toInstant
        if (Instant.now().isBefore(expireInstant)) {
          val username: String = record.eval("user.username").toString
          val userType: String = record.eval("user.userType").toString
          val userId = DomainUserId(DomainUserType.withName(userType), username)
          val newExpiration = Instant.now().plus(tokenDuration)
          record.setProperty(Classes.UserReconnectToken.Fields.ExpireTime, Date.from(newExpiration))
          record.save()
          Some(userId)
        } else {
          None
        }
      })
  }

  /**
   * Sets the last login time for a domain user.
   */
  def setLastLoginForUser(userId: DomainUserId, lastLoginTime: Instant): Try[Unit] = withDb { db =>
    val params = Map(
      Params.Username -> userId.username,
      Params.UserType -> userId.userType.toString.toLowerCase,
      Params.LastLogin -> Date.from(lastLoginTime)
    )
    OrientDBUtil.mutateOneDocument(db, SetLastLoginTimeCommand, params)
  }

  private val SetLastLoginTimeCommand =
    "UPDATE User SET lastLogin = :lastLogin WHERE username = :username and userType = :userType"

  /**
   * @return The number of normal users in the system.
   */
  def getNormalUserCount(): Try[Long] = withDb { db =>
    OrientDBUtil
      .getDocument(db, GetNormalUserCountQuery)
      .map(_.field("count").asInstanceOf[Long])
  }

  private[this] val GetNormalUserCountQuery = "SELECT count(*) as count FROM User WHERE userType = 'normal'"

  /**
   * A helper method to generate the next anonymous user name.
   */
  private[this] def nextAnonymousUsername(): Try[String] = withDb { db =>
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


object DomainUserStore {

  import schema.UserClass._

  val UserDoesNotExistMessage = "User does not exist"

  object Params {
    val Username = "username"
    val UserType = "userType"
    val LastLogin = "lastLogin"
  }

  private[domain] def findUserRid(userId: DomainUserId, db: ODatabaseDocument): Try[Option[ORID]] = {
    OrientDBUtil.findIdentityFromSingleValueIndex(db, Indices.UsernameUserType, List(userId.username, userId.userType.toString.toLowerCase))
  }

  private[domain] def getUserRid(userId: DomainUserId, db: ODatabaseDocument): Try[ORID] = {
    this.getUserRid(userId.username, userId.userType, db)
  }

  private[domain] def getUserRid(username: String, userType: DomainUserType.Value, db: ODatabaseDocument): Try[ORID] = {
    db.activateOnCurrentThread()
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Indices.UsernameUserType, List(username, userType.toString.toLowerCase))
  }

  private[domain] def getDomainUsersRids(userIds: List[DomainUserId], db: ODatabaseDocument): Try[List[ORID]] = {
    val keys = userIds.map(userId => new OCompositeKey(List(userId.username, userId.userType.toString.toLowerCase).asJava))
    OrientDBUtil.getIdentitiesFromSingleValueIndex(db, Indices.UsernameUserType, keys)
  }

  private[domain] def domainUserToDoc(obj: DomainUser): ODocument = {
    val doc = new ODocument(ClassName)
    doc.setProperty(Fields.UserType, obj.userType.toString.toLowerCase)
    doc.setProperty(Fields.Username, obj.username)
    obj.firstName.foreach(doc.setProperty(Fields.FirstName, _))
    obj.lastName.foreach(doc.setProperty(Fields.LastName, _))
    obj.displayName.foreach(doc.setProperty(Fields.DisplayName, _))
    obj.email.foreach(doc.setProperty(Fields.Email, _))
    obj.lastLogin.foreach(d => doc.setProperty(Fields.LastLogin, Date.from(d)))
    doc.setProperty(Fields.Disabled, obj.disabled)
    doc.setProperty(Fields.Deleted, obj.deleted)
    obj.deletedUsername.foreach(doc.setProperty(Fields.DeletedUsername, _))
    doc
  }

  private[domain] def docToDomainUser(doc: ODocument): DomainUser = {
    DomainUser(
      DomainUserType.withName(doc.getProperty(Fields.UserType)),
      doc.getProperty(Fields.Username),
      Option(doc.getProperty(Fields.FirstName)),
      Option(doc.getProperty(Fields.LastName)),
      Option(doc.getProperty(Fields.DisplayName)),
      Option(doc.getProperty(Fields.Email)),
      Option(doc.getProperty(Fields.LastLogin).asInstanceOf[Date]).map(_.toInstant),
      doc.getProperty(Fields.Disabled),
      doc.getProperty(Fields.Deleted),
      Option(doc.getProperty(Fields.DeletedUsername)))
  }

  private val ReconnectTokenGenerator = new RandomStringGenerator(32)

  private[this] val DeletedUsernameGenerator = new RandomStringGenerator(36, RandomStringGenerator.Base64)

  private def generateDeletedUsername(): String = {
    DeletedUsernameGenerator.nextString()
  }
}
