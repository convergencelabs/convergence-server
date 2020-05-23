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

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try

import com.convergencelabs.convergence.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.convergence.server.datastore.OrientDBUtil
import com.convergencelabs.convergence.server.datastore.convergence.schema.ConfigClass
import com.convergencelabs.convergence.server.datastore.convergence.schema.ConfigClass.Fields
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument

import grizzled.slf4j.Logging
import scala.util.Success
import scala.util.Failure
import java.time.Duration

object ConfigKeys {
  object Namespaces {
    val Enabled = "namespaces.enabled"
    val UserNamespacesEnabled = "namespaces.user-namespaces-enabled"
    val DefaultNamespace = "namespaces.default-namespace"
  }

  object Passwords {
    val MinimumLength = "passwords.minimum-length"
    val RequireNumeric = "passwords.require-numeric"
    val RequireLowerCase = "passwords.require-lower-case"
    val RequireUpperCase = "passwords.require-upper-case"
    val RequireSpecialCharacters = "passwords.require-special-characters"
  }

  object Sessions {
    val Timeout = "sessions.timeout"
  }

  private[this] val typeMaps: Map[String, Set[Class[_]]] = Map(
    Namespaces.Enabled -> Set(classOf[Boolean], classOf[java.lang.Boolean]),
    Namespaces.UserNamespacesEnabled -> Set(classOf[Boolean], classOf[java.lang.Boolean]),
    Namespaces.DefaultNamespace -> Set(classOf[String]),

    Passwords.MinimumLength -> Set(classOf[Int], classOf[java.lang.Integer]),
    Passwords.RequireNumeric -> Set(classOf[Boolean], classOf[java.lang.Boolean]),
    Passwords.RequireLowerCase -> Set(classOf[Boolean], classOf[java.lang.Boolean]),
    Passwords.RequireUpperCase -> Set(classOf[Boolean], classOf[java.lang.Boolean]),
    Passwords.RequireSpecialCharacters -> Set(classOf[Boolean], classOf[java.lang.Boolean]),

    Sessions.Timeout ->  Set(classOf[Int], classOf[java.lang.Integer]))

  def validateConfig(key: String, value: Any): Try[Unit] = {
    typeMaps.get(key) match {
      case Some(classes) => 
        if (classes.contains(value.getClass)) {
          Success(())
        } else {
          Failure(new IllegalArgumentException(s"'$key' must be of type ${classes.mkString(", ")} but was of type: ${value.getClass.getName}"))
        }
      case None =>
        Failure(new IllegalArgumentException(s"'$key' is not a valid configuration key."))
    }
  }
}

case class ConfigNotFound(key: String) extends Exception(s"The required config '$key' does not exist")

object ConfigStore {

  object Params {
    val Key = "key"
    val Keys = "keys"
    val Value = "value"
  }

  def configToDoc(config: (String, Any), db: ODatabaseDocument): Try[ODocument] = Try {
    val (key, value) = config
    val doc = db.newInstance(ConfigClass.ClassName).asInstanceOf[ODocument]
    doc.setProperty(Fields.Key, key)
    doc.setProperty(Fields.Value, value)
    doc
  }

  def docToConfig(doc: ODocument): (String, Any) = {
    val key: String = doc.getProperty(Fields.Key)
    val value: Any = processValue(key, doc.getProperty(Fields.Value))
    (key, value)
  }

  def toInteger(value: Any): Integer = {
    value match {
      case v: Integer => v
      case v: BigInt  => v.intValue()
      case v: Long    => v.intValue()
      case v: String  => Integer.parseInt(v)
    }
  }

  def processValue(key: String, value: Any): Any = {
    key match {
      case ConfigKeys.Passwords.MinimumLength => toInteger(value)
      case ConfigKeys.Sessions.Timeout        => toInteger(value)
      case _                                  => value
    }
  }
}

class ConfigStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import ConfigStore._

  def setConfig(key: String, value: Any): Try[Unit] = withDb { db =>
    val processedValue = processValue(key, value)
    ConfigKeys.validateConfig(key, processedValue)
    val command = "UPDATE Config SET value = :value UPSERT WHERE key = :key"
    val params = Map(Params.Key -> key, Params.Value -> processedValue)
    OrientDBUtil.commandReturningCount(db, command, params).map(_ => ())
  }

  def setConfigs(configs: Map[String, Any]): Try[Unit] = {
    Try {
      // todo do this in two steps so we know that all the configs are good before processing them
      //   later if we get this in a transaction then we can do them as we go.
      val processedConfigs = configs.map {case (k, v) => (k, processValue(k, v))}
      processedConfigs.foreach { case (k, v) => ConfigKeys.validateConfig(k, v).get }
      processedConfigs.foreach {
        case (key, value) => setConfig(key, value).get
      }
    }
  }

  def getSessionTimeout(): Try[Duration] = {
    getInteger(ConfigKeys.Sessions.Timeout).map { minutes =>
      Duration.ofMinutes(minutes.toLong)
    }
  }

  def getInteger(key: String): Try[Integer] = {
    getRequiredConfig(key).map(_.asInstanceOf[Integer])
  }

  def getRequiredConfig(key: String): Try[Any] = {
    getConfig(key).flatMap {
      case Some(config) =>
        Success(config)
      case None =>
        Failure(ConfigNotFound(key))
    }
  }

  def getConfig(key: String): Try[Option[Any]] = {
    getConfigs(List(key)).map(_.get(key))
  }

  def getConfigs(keys: List[String]): Try[Map[String, Any]] = withDb { db =>
    val params = Map(Params.Keys -> keys.asJava)
    OrientDBUtil.query(db, "SELECT FROM Config WHERE key IN :keys", params)
      .map(_.map(ConfigStore.docToConfig))
      .map(_.toMap)
  }

  def getConfigsByFilter(filters: List[String]): Try[Map[String, Any]] = withDb { db =>
    val params = scala.collection.mutable.Map[String, Any]()
    var paramNo = 0
    val where = "WHERE " + filters.map { filter =>
      params += (paramNo.toString -> filter.replace("*", "%").toLowerCase)
      val like = s" LIKE :p$paramNo"
      paramNo = paramNo + 1
      like
    }.mkString(" OR ")
    val query = "SELECT FROM Config " + where
    OrientDBUtil.query(db, query, params.toMap)
      .map(_.map(ConfigStore.docToConfig))
      .map(_.toMap)
  }

  def getConfigs(): Try[Map[String, Any]] = withDb { db =>
    OrientDBUtil.query(db, "SELECT FROM Config")
      .map(_.map(ConfigStore.docToConfig))
      .map(_.toMap)
  }

  def removeConfig(key: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM Config WHERE key = :key"
    val params = Map(Params.Key -> key)
    OrientDBUtil.mutateOneDocument(db, command, params).map(_ => ())
  }
}
