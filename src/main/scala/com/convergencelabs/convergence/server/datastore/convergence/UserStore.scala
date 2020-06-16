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

import com.convergencelabs.convergence.server.datastore.convergence.schema.UserClass
import com.convergencelabs.convergence.server.datastore.domain.PasswordUtil
import com.convergencelabs.convergence.server.datastore.{AbstractDatabasePersistence, DuplicateValueException, EntityNotFoundException, OrientDBUtil}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.util.{Failure, Try}

/**
 * Manages the persistence of Users.  This class manages both user records
 * as well as user credentials for users authenticated by Convergence itself.
 *
 * @constructor Creates a new UserStore using the provided connection pool to
 *              connect to the database
 * @param dbProvider The database pool to use.
 */
class UserStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import UserStore._

  def createUser(user: User, password: String, bearerToken: String): Try[Unit] = {
    createUserWithPasswordHash(user, PasswordUtil.hashPassword(password), bearerToken)
  }

  def createUserWithPasswordHash(user: User, passwordHash: String, bearerToken: String): Try[Unit] = tryWithDb { db =>
    val userDoc = UserStore.userToDoc(user, Some(bearerToken), Some(passwordHash), Some(new Date()))
    db.save(userDoc)
    ()
  } recoverWith handleDuplicateValue

  def updateUser(user: User): Try[Unit] = withDb { db =>
    val updatedDoc = UserStore.userToDoc(user)
    OrientDBUtil
      .findDocumentFromSingleValueIndex(db, UserClass.Indices.Username, user.username)
      .map {
        case Some(doc) =>
          doc.merge(updatedDoc, true, false)
          db.save(doc)
          ()
        case None =>
          throw EntityNotFoundException()
      } recoverWith handleDuplicateValue
  }

  def deleteUser(username: String): Try[Unit] = tryWithDb { db =>
    OrientDBUtil.deleteFromSingleValueIndex(db, UserClass.Indices.Username, username)
  }

  /**
   * Gets a single user by username.
   *
   * @param username The username of the user to retrieve.
   * @return Some(User) if a user with the specified username exists, or None if no such user exists.
   */
  def getUserByUsername(username: String): Try[Option[User]] = withDb { db =>
    OrientDBUtil.findDocumentFromSingleValueIndex(db, UserClass.Indices.Username, username).map(_.map(UserStore.docToUser))
  }

  // TODO add an ordering ability.
  def getUsers(filter: Option[String], offset: QueryOffset, limit: QueryLimit): Try[List[User]] = withDb { db =>
    val where = " WHERE username.toLowerCase() LIKE :searchString OR displayName.toLowerCase() LIKE :searchString OR email.toLowerCase() LIKE :searchString"
    val baseQuery = "SELECT FROM User" + filter.map(_ => where).getOrElse("") + " ORDER BY username"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    val params = filter match {
      case Some(searchFilter) =>
        Map("searchString" -> s"%${searchFilter.toLowerCase}%")
      case None =>
        Map[String, Any]()
    }

    OrientDBUtil.query(db, query, params).map(docs => docs.map(UserStore.docToUser))
  }

  /**
   * Checks to see if a given username exists in the system.
   *
   * @param username The username to check existence for.
   * @return true if the user exists, false otherwise.
   */
  def userExists(username: String): Try[Boolean] = withDb { db =>
    OrientDBUtil.indexContains(db, UserClass.Indices.Username, username)
  }

  private[this] val SetUserPasswordQuery = "UPDATE User SET passwordHash = :passwordHash, passwordLastSet = :passwordLastSet WHERE username = :username"

  /**
   * Set the password for an existing user by username.
   *
   * @param username The unique username of the user.
   * @param password The new password to use for internal authentication
   */
  def setUserPassword(username: String, password: String): Try[Unit] = withDb { db =>
    val passwordHash = PasswordUtil.hashPassword(password)
    val params = Map(Params.Username -> username, Params.PasswordHash -> passwordHash, Params.PasswordLastSet -> new Date())
    OrientDBUtil.mutateOneDocument(db, SetUserPasswordQuery, params)
      .recoverWith {
        case _: EntityNotFoundException =>
          Failure(EntityNotFoundException("Can't set the password for a user that does not exist."))
      }
  }

  private[this] val GetPasswordHashQuery = "SELECT passwordHash FROM User WHERE username = :username"

  def getUserPasswordHash(username: String): Try[Option[String]] = withDb { db =>
    val params = Map(Params.Username -> username)
    OrientDBUtil.findDocument(db, GetPasswordHashQuery, params)
      .map(_.flatMap(doc => Option(doc.getProperty(UserClass.Fields.PasswordHash).asInstanceOf[String])))
  }

  private[this] val ValidateCredentialsQuery = "SELECT passwordHash, username FROM User WHERE username = :username"

  /**
   * Validated that the username and password combination are valid.
   *
   * @param username The username of the user to check the password for.
   * @param password The cleartext password of the user
   * @return true if the username and password match, false otherwise.
   */
  def validateCredentials(username: String, password: String): Try[Boolean] = withDb { db =>
    val params = Map(Params.Username -> username)
    OrientDBUtil.findDocument(db, ValidateCredentialsQuery, params)
      .map {
        case Some(doc) =>
          val pwhash: String = doc.getProperty(UserClass.Fields.PasswordHash)
          if (PasswordUtil.checkPassword(password, pwhash)) {
            setLastLogin(username, Instant.now())
            true
          } else {
            false
          }
        case None =>
          false
      }
  }

  private[this] val LoginQuery = "SELECT passwordHash, bearerToken FROM User WHERE username = :username"

  def login(username: String, password: String): Try[Option[String]] = withDb { db =>
    val params = Map(Params.Username -> username)
    OrientDBUtil.findDocument(db, LoginQuery, params).map(_.flatMap { doc =>
      val pwhash: String = doc.getProperty(UserClass.Fields.PasswordHash)
      if (PasswordUtil.checkPassword(password, pwhash)) {
        val token = doc.getProperty(UserClass.Fields.BearerToken).asInstanceOf[String]
        Some(token)
      } else {
        None
      }
    })
  }

  private[this] val ValidateBearerTokenQuery = "SELECT username FROM User WHERE bearerToken = :bearerToken"

  def validateBearerToken(bearerToken: String): Try[Option[String]] = withDb { db =>
    val params = Map(Params.BearerToken -> bearerToken)
    OrientDBUtil.findDocument(db, ValidateBearerTokenQuery, params)
      .map(_.map(doc => doc.getProperty(UserClass.Fields.Username).asInstanceOf[String]))
  }

  private[this] val SetBearerTokenCommand = "UPDATE User SET bearerToken = :bearerToken WHERE username = :username"

  def setBearerToken(username: String, bearerToken: String): Try[Unit] = withDb { db =>
    val params = Map(Params.BearerToken -> bearerToken, Params.Username -> username)
    OrientDBUtil.mutateOneDocument(db, SetBearerTokenCommand, params)
  }

  private[this] val GetBearerTokenCommand = "SELECT bearerToken FROM User WHERE username = :username"

  def getBearerToken(username: String): Try[Option[String]] = withDb { db =>
    val params = Map(Params.Username -> username)
    OrientDBUtil.findDocument(db, GetBearerTokenCommand, params).map(_.map(_.getProperty("bearerToken").asInstanceOf[String]))
  }

  private[this] val SetLastLoginCommand = "UPDATE User SET lastLogin = :lastLogin WHERE username = :username"

  def setLastLogin(username: String, lastLogin: Instant): Try[Unit] = withDb { db =>
    val params = Map(Params.LastLogin -> Date.from(lastLogin), Params.Username -> username)
    OrientDBUtil.mutateOneDocument(db, SetLastLoginCommand, params)
  }

  private[this] def handleDuplicateValue[T]: PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case UserClass.Indices.Username =>
          Failure(DuplicateValueException(UserClass.Fields.Username))
        case UserClass.Indices.Email =>
          Failure(DuplicateValueException(UserClass.Fields.Email))
        case _ =>
          Failure(e)
      }
  }
}

object UserStore {

  object Params {
    val Username = "username"
    val BearerToken = "bearerToken"
    val PasswordHash = "passwordHash"
    val PasswordLastSet = "passwordLastSet"
    val LastLogin = "lastLogin"
  }

  def docToUser(doc: ODocument): User = {
    User(
      doc.getProperty(UserClass.Fields.Username),
      doc.getProperty(UserClass.Fields.Email),
      doc.getProperty(UserClass.Fields.FirstName),
      doc.getProperty(UserClass.Fields.LastName),
      doc.getProperty(UserClass.Fields.DisplayName),
      Option(doc.getProperty(UserClass.Fields.LastLogin).asInstanceOf[Date]).map(_.toInstant()))
  }

  def userToDoc(user: User, bearerToken: Option[String] = None, passwordHash: Option[String] = None, passwordLastSet: Option[Date] = None): ODocument = {
    val doc = new ODocument(UserClass.ClassName)
    doc.setProperty(UserClass.Fields.Username, user.username)
    doc.setProperty(UserClass.Fields.Email, user.email)
    doc.setProperty(UserClass.Fields.FirstName, user.firstName)
    doc.setProperty(UserClass.Fields.LastName, user.lastName)
    doc.setProperty(UserClass.Fields.DisplayName, user.displayName)
    bearerToken.foreach(doc.setProperty(UserClass.Fields.BearerToken, _))
    passwordHash.foreach(doc.setProperty(UserClass.Fields.PasswordHash, _))
    passwordLastSet.foreach(doc.setProperty(UserClass.Fields.PasswordLastSet, _))
    doc
  }

  def getUserRid(username: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, UserClass.Indices.Username, username)
  }

  case class User(username: String,
                  email: String,
                  firstName: String,
                  lastName: String,
                  displayName: String,
                  lastLogin: Option[Instant])

}