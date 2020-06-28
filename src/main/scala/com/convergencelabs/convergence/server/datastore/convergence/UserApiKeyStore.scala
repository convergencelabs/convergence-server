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

import com.convergencelabs.convergence.server.datastore.{AbstractDatabasePersistence, DuplicateValueException, OrientDBUtil}
import com.convergencelabs.convergence.server.datastore.convergence.schema.UserApiKeyClass
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.util.{Failure, Try}

case class UserApiKey(username: String,
                      name: String,
                      key: String,
                      enabled: Boolean,
                      lastUsed: Option[Instant])

object UserApiKeyStore {

  object Params {
    val Key = "key"
    val Username = "username"
    val Name = "name"
    val Enabled = "enabled"
    val LastUsed = "lastUsed"
  }

  def docToApiKey(doc: ODocument): UserApiKey = {
    UserApiKey(
      doc.eval("user.username").asInstanceOf[String],
      doc.getProperty(UserApiKeyClass.Fields.Name),
      doc.getProperty(UserApiKeyClass.Fields.Key),
      doc.getProperty(UserApiKeyClass.Fields.Enabled),
      Option(doc.getProperty(UserApiKeyClass.Fields.LastUsed).asInstanceOf[Date]).map(_.toInstant()))
  }

  def userApiKeyToDoc(apiKey: UserApiKey, userRid: ORID): ODocument = {
    val doc = new ODocument(UserApiKeyClass.ClassName)
    doc.setProperty(UserApiKeyClass.Fields.User, userRid)
    doc.setProperty(UserApiKeyClass.Fields.Name, apiKey.name)
    doc.setProperty(UserApiKeyClass.Fields.Key, apiKey.key)
    doc.setProperty(UserApiKeyClass.Fields.Enabled, apiKey.enabled)
    apiKey.lastUsed.foreach(l => doc.setProperty(UserApiKeyClass.Fields.LastUsed, Date.from(l)))
    doc
  }
}

/**
 * Manages the persistence of User API Keys.
 *
 * @constructor Creates a new UserApiKeyStore using the provided database provider.
 * @param dbProvider The database provider to use for persistence
 */
class UserApiKeyStore(val dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider) with Logging {

  import UserApiKeyStore._

  def createKey(apiKey: UserApiKey): Try[Unit] = withDb { db =>
    val UserApiKey(username, name, key, enabled, _) = apiKey
    val command =
      """
        |INSERT INTO
        |  UserApiKey 
        |SET
        |  user = (SELECT FROM User WHERE username = :username),
        |  name = :name,
        |  key = :key,
        |  enabled = :enabled""".stripMargin

    val params = Map(
      Params.Username -> username,
      Params.Name -> name,
      Params.Key -> key,
      Params.Enabled -> enabled)

    OrientDBUtil.commandReturningCount(db, command, params).map(_ => ())
  } recoverWith handleDuplicateValue

  private[this] val GetKeysForUserQuery = "SELECT * FROM UserApiKey WHERE user.username = :username"

  def getKeysForUser(username: String): Try[Set[UserApiKey]] = withDb { db =>
    val params = Map(Params.Username -> username)
    OrientDBUtil.queryAndMap(db, GetKeysForUserQuery, params) { doc => docToApiKey(doc) }.map(_.toSet)
  }

  private[this] val GetKeyForUserQuery = "SELECT * FROM UserApiKey WHERE user.username = :username AND key = :key"

  def getKeyForUser(username: String, key: String): Try[Option[UserApiKey]] = withDb { db =>
    val params = Map(Params.Username -> username, Params.Key -> key)
    OrientDBUtil.findDocumentAndMap(db, GetKeyForUserQuery, params) { doc => docToApiKey(doc) }
  }

  private[this] val DeleteKeyCommand = "DELETE FROM UserApiKey WHERE key = :key AND user.username = :username"

  def deleteKey(apiKey: String, username: String): Try[Unit] = withDb { db =>
    val params = Map(Params.Key -> apiKey, Params.Username -> username)
    OrientDBUtil.mutateOneDocument(db, DeleteKeyCommand, params)
  }

  private[this] val ValidateUserApiKeyQuery = "SELECT user.username as username FROM UserApiKey WHERE key = :key AND enabled = true"

  def validateUserApiKey(apiKey: String): Try[Option[String]] = withDb { db =>
    val params = Map(Params.Key -> apiKey)
    OrientDBUtil.findDocument(db, ValidateUserApiKeyQuery, params)
      .map(_.map(doc => doc.getProperty(Params.Username).asInstanceOf[String]))
  }

  private[this] val SetLastUsedForKeyCommand = "UPDATE UserApiKey SET lastUsed = :lastUsed WHERE key = :key"

  def setLastUsedForKey(apiKey: String, lastUsed: Instant): Try[Unit] = withDb { db =>
    val params = Map(Params.Key -> apiKey, Params.LastUsed -> Date.from(lastUsed))
    OrientDBUtil.mutateOneDocument(db, SetLastUsedForKeyCommand, params)
  }

  private[this] val UpdateKey = "UPDATE UserApiKey SET name = :name, enabled = :enabled WHERE key = :key AND user.username = :username"

  def updateKeyKey(apiKey: String, username: String, name: String, enabled: Boolean): Try[Unit] = withDb { db =>
    val params = Map(Params.Key -> apiKey, Params.Username -> username, Params.Name -> name, Params.Enabled -> enabled)
    OrientDBUtil.mutateOneDocument(db, UpdateKey, params)
  } recoverWith handleDuplicateValue

  private[this] def handleDuplicateValue[T]: PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case UserApiKeyClass.Indices.Key =>
          Failure(DuplicateValueException(UserApiKeyClass.Fields.Key))
        case UserApiKeyClass.Indices.UserName =>
          Failure(DuplicateValueException("user_name"))
        case _ =>
          Failure(e)
      }
  }
}
