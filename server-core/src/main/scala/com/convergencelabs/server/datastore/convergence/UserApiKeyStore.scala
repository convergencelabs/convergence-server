package com.convergencelabs.server.datastore.convergence

import java.time.Instant
import java.util.Date

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.convergence.schema.UserApiKeyClass
import com.convergencelabs.server.db.DatabaseProvider
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging

case class UserApiKey(
  username: String,
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
 *
 * @param dbProvider The database provider to use for persistence
 */
class UserApiKeyStore(
  val dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import UserApiKeyStore._

  def createKey(apiKey: UserApiKey): Try[Unit] = withDb { db =>
    val UserApiKey(username, name, key, enabled, lastUsed) = apiKey
    val queryStirng = """
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

    OrientDBUtil.mutateOneDocument(db, queryStirng, params)
  }

  private[this] val DeleteKeyCommand = "DELETE FROM UserApiKey WHERE key = :key"

  def deleteKey(apiKey: String): Try[Unit] = tryWithDb { db =>
    val params = Map(Params.Key -> apiKey)
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
    val params = Map(Params.Key -> apiKey, Params.LastUsed -> lastUsed)
    OrientDBUtil.mutateOneDocument(db, SetLastUsedForKeyCommand, params)
  }
  
  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case UserApiKeyClass.Indices.Key =>
          Failure(DuplicateValueException(UserApiKeyClass.Fields.Key))
        case UserApiKeyClass.Indices.UserName =>
          Failure(DuplicateValueException("User and Name"))
        case _ =>
          Failure(e)
      }
  }
}
