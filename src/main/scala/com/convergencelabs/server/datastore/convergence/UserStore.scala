/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

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
import com.convergencelabs.server.datastore.convergence.schema.UserClass
import com.convergencelabs.server.datastore.domain.PasswordUtil
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.util.RandomStringGenerator
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging

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

  case class User(
    username: String,
    email: String,
    firstName: String,
    lastName: String,
    displayName: String,
    lastLogin: Option[Instant])
}

/**
 * Manages the persistence of Users.  This class manages both user records
 * as well as user credentials for users authenticated by Convergence itself.
 *
 * @constructor Creates a new UserStore using the provided connection pool to
 * connect to the database
 *
 * @param dbPool The database pool to use.
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
  } recoverWith (handleDuplicateValue)

  def updateUser(user: User): Try[Unit] = withDb { db =>
    val updatedDoc = UserStore.userToDoc(user)
    OrientDBUtil
      .findDocumentFromSingleValueIndex(db, UserClass.Indices.Username, user.username)
      .map(_ match {
        case Some(doc) =>
          doc.merge(updatedDoc, true, false)
          db.save(doc)
          ()
        case None =>
          throw EntityNotFoundException()
      }) recoverWith (handleDuplicateValue)
  }

  def deleteUser(username: String): Try[Unit] = tryWithDb { db =>
    OrientDBUtil.deleteFromSingleValueIndex(db, UserClass.Indices.Username, username)
  }

  /**
   * Gets a single user by username.
   *
   * @param username The username of the user to retrieve.
   *
   * @return Some(User) if a user with the specified username exists, or None if no such user exists.
   */
  def getUserByUsername(username: String): Try[Option[User]] = withDb { db =>
    OrientDBUtil.findDocumentFromSingleValueIndex(db, UserClass.Indices.Username, username).map(_.map(UserStore.docToUser(_)))
  }

  // TODO add an ordering ability.
  def getUsers(filter: Option[String], limit: Option[Int], offset: Option[Int]): Try[List[User]] = withDb { db =>
    val where = " WHERE username.toLowerCase() LIKE :searchString OR displayName.toLowerCase() LIKE :searchString OR email.toLowerCase() LIKE :searchString"
    val baseQuery = "SELECT FROM User" + filter.map(_ => where).getOrElse("") + " ORDER BY username"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    val params = filter match {
      case Some(searchFilter) =>
        Map("searchString" -> s"%${searchFilter.toLowerCase}%")
      case None =>
        Map[String, Any]()
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
        case cause: EntityNotFoundException =>
          Failure(new EntityNotFoundException("Can't set the password for a user that does not exist."))
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
   *
   * @return true if the username and passowrd match, false otherwise.
   */
  def validateCredentials(username: String, password: String): Try[Boolean] = withDb { db =>
    val params = Map(Params.Username -> username)
    OrientDBUtil.findDocument(db, ValidateCredentialsQuery, params)
      .map(_ match {
        case Some(doc) =>
          val pwhash: String = doc.getProperty(UserClass.Fields.PasswordHash)
          PasswordUtil.checkPassword(password, pwhash) match {
            case true => {
              setLastLogin(username, Instant.now())
              true
            }
            case false =>
              false
          }
        case None =>
          false
      })
  }

  private[this] val LoginQuery = "SELECT passwordHash, bearerToken FROM User WHERE username = :username"
  def login(username: String, password: String): Try[Option[String]] = withDb { db =>
    val params = Map(Params.Username -> username)
    OrientDBUtil.findDocument(db, LoginQuery).map(_.flatMap { doc =>
      val pwhash: String = doc.getProperty(UserClass.Fields.PasswordHash)
      PasswordUtil.checkPassword(password, pwhash) match {
        case true =>
          val token = doc.getProperty(UserClass.Fields.BearerToken).asInstanceOf[String]
          Some(token)
        case false =>
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

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
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
