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

package com.convergencelabs.convergence.server.backend.datastore.domain.config

import com.convergencelabs.convergence.server.backend.datastore.domain.config.DomainConfigStore.ConfigKeys
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.domain.jwt.JwtKeyPair
import com.convergencelabs.convergence.server.model.domain.{CollectionConfig, ModelSnapshotConfig}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import grizzled.slf4j.Logging

import java.time.Duration
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * The DomainConfigStore store domain wide configurations within a domain
 * database.
 *
 * @param dbProvider The DatabaseProvider that provides a connection to
 *                   the database.
 */
class DomainConfigStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import ConfigKeys._

  def initializeDomainConfig(tokenKeyPair: JwtKeyPair,
                             collectionConfig: CollectionConfig,
                             modelSnapshotConfig: ModelSnapshotConfig,
                             anonymousAuthEnabled: Boolean,
                             reconnectTokenValidityMinutes: Long): Try[Unit] = withDb { db =>
    for {
      _ <- OrientDBUtil.command(db, "DELETE FROM DomainConfig")
      _ <- setModelSnapshotConfig(modelSnapshotConfig, Some(db))
      _ <- setCollectionConfig(collectionConfig, Some(db))
      _ <- setAdminKeyPair(tokenKeyPair, Some(db))
      _ <- setAnonymousAuthEnabled(anonymousAuthEnabled, Some(db))
      _ <- setReconnectTokenTimeoutMinutes(reconnectTokenValidityMinutes, Some(db))
    } yield ()
  }

  def isInitialized(): Try[Boolean] = withDb { db =>
    OrientDBUtil
      .getDocument(db, "SELECT count(*) AS count FROM DomainConfig")
      .map(_.getProperty("count").asInstanceOf[Long] > 0)
  }

  def isAnonymousAuthEnabled(): Try[Boolean] = {
    val keys = List(Authentication.AnonymousAuthEnabled)
    getConfigs(keys).map { configs =>
      configs(Authentication.AnonymousAuthEnabled).asInstanceOf[Boolean]
    }
  }

  def setAnonymousAuthEnabled(enabled: Boolean, db: Option[ODatabaseDocument] = None): Try[Unit] = {
    setConfigs(Map(Authentication.AnonymousAuthEnabled -> enabled), db)
  }

  def getReconnectTokenTimeoutMinutes(): Try[Long] = {
    val keys = List(Authentication.ReconnectTokenValidityMinutes)
    getConfigs(keys).map { configs =>
      configs(Authentication.ReconnectTokenValidityMinutes).asInstanceOf[Long]
    }
  }

  def setReconnectTokenTimeoutMinutes(minutes: Long, db: Option[ODatabaseDocument] = None): Try[Unit] = {
    setConfigs(Map(Authentication.ReconnectTokenValidityMinutes -> minutes), db)
  }

  def getModelSnapshotConfig(): Try[ModelSnapshotConfig] = {
    val keys = List(
      Model.Snapshots.Enabled,
      Model.Snapshots.TriggerByVersion,
      Model.Snapshots.LimitedByVersion,
      Model.Snapshots.MinVersionInterval,
      Model.Snapshots.MaxVersionInterval,
      Model.Snapshots.TriggerByTime,
      Model.Snapshots.LimitedByTime,
      Model.Snapshots.MinTimeInterval,
      Model.Snapshots.MaxTimeInterval,
    )
    getConfigs(keys).map { configs =>
      ModelSnapshotConfig(
        configs(Model.Snapshots.Enabled).asInstanceOf[Boolean],
        configs(Model.Snapshots.TriggerByVersion).asInstanceOf[Boolean],
        configs(Model.Snapshots.LimitedByVersion).asInstanceOf[Boolean],
        configs(Model.Snapshots.MinVersionInterval).asInstanceOf[Long],
        configs(Model.Snapshots.MaxVersionInterval).asInstanceOf[Long],
        configs(Model.Snapshots.TriggerByTime).asInstanceOf[Boolean],
        configs(Model.Snapshots.LimitedByTime).asInstanceOf[Boolean],
        Duration.ofMillis(configs(Model.Snapshots.MinTimeInterval).asInstanceOf[Long]),
        Duration.ofMillis(configs(Model.Snapshots.MaxTimeInterval).asInstanceOf[Long]),
      )
    }
  }

  def setModelSnapshotConfig(modelSnapshotConfig: ModelSnapshotConfig, db: Option[ODatabaseDocument] = None): Try[Unit] = {
    val updates = Map(
      Model.Snapshots.Enabled -> modelSnapshotConfig.snapshotsEnabled,
      Model.Snapshots.TriggerByVersion -> modelSnapshotConfig.triggerByVersion,
      Model.Snapshots.LimitedByVersion -> modelSnapshotConfig.limitedByVersion,
      Model.Snapshots.MinVersionInterval -> modelSnapshotConfig.minimumVersionInterval,
      Model.Snapshots.MaxVersionInterval -> modelSnapshotConfig.maximumVersionInterval,
      Model.Snapshots.TriggerByTime -> modelSnapshotConfig.triggerByTime,
      Model.Snapshots.LimitedByTime -> modelSnapshotConfig.limitedByTime,
      Model.Snapshots.MinTimeInterval -> modelSnapshotConfig.minimumTimeInterval.toMillis,
      Model.Snapshots.MaxTimeInterval -> modelSnapshotConfig.maximumTimeInterval.toMillis,
    )
    setConfigs(updates, db)
  }

  def getCollectionConfig(): Try[CollectionConfig] = {
    val keys = List(Collection.AutoCreate)
    getConfigs(keys).map { configs =>
      CollectionConfig(configs(Collection.AutoCreate).asInstanceOf[Boolean])
    }
  }

  def setCollectionConfig(config: CollectionConfig, db: Option[ODatabaseDocument] = None): Try[Unit] = {
    setConfigs(Map(Collection.AutoCreate -> config.autoCreate), db)
  }

  def getAdminKeyPair(): Try[JwtKeyPair] = {
    val keys = List(
      Authentication.AdminJwtPublicKey,
      Authentication.AdminJwtPrivateKey
    )
    getConfigs(keys).map { configs =>
      JwtKeyPair(
        configs(Authentication.AdminJwtPublicKey).asInstanceOf[String],
        configs(Authentication.AdminJwtPrivateKey).asInstanceOf[String]
      )
    }
  }

  def setAdminKeyPair(pair: JwtKeyPair, db: Option[ODatabaseDocument] = None): Try[Unit] = {
    val updates = Map(
      Authentication.AdminJwtPublicKey -> pair.publicKey,
      Authentication.AdminJwtPrivateKey -> pair.privateKey)
    setConfigs(updates, db)
  }

  //
  // Private helper methods
  //

  private[this] val GetConfigsQuery = "SELECT FROM DomainConfig where key IN :keys"

  private[this] def getConfigs(keys: List[String]): Try[Map[String, Any]] = withDb { db =>
    val params = Map("keys" -> keys.asJava)
    OrientDBUtil.query(db, GetConfigsQuery, params).map(_.map { doc =>
      val key: String = doc.getProperty("key")
      val value: Any = doc.getProperty("value")
      (key, value)
    }.toMap)
  }

  private[this] def setConfigs(updates: Map[String, Any], db: Option[ODatabaseDocument] = None): Try[Unit] = withDb(db) { db =>
    val (script, params) = createUpdateScript(updates)
    OrientDBUtil.execute(db, script, params).map(_ => ())
  }

  private[this] def createUpdateScript(values: Map[String, Any]): (String, Map[String, Any]) = {
    var params = Map[String, Any]()
    var updates = List[String]()
    values.foreach { case (key, value) =>
      val valParam = s"value${updates.size}"
      val keyParam = s"key${updates.size}"
      updates = updates :+ s"UPDATE DomainConfig SET key = :$keyParam, value = :$valParam UPSERT WHERE key = :$keyParam;"
      params ++= Map(keyParam -> key, valParam -> value)
    }

    (updates.mkString("\n"), params)
  }
}

object DomainConfigStore {

  private[DomainConfigStore] object ConfigKeys {
    object Authentication {
      val ReconnectTokenValidityMinutes = "authentication.reconnectTokenValidityMinutes"
      val AnonymousAuthEnabled = "authentication.anonymousAuthEnabled"
      val AdminJwtPublicKey = "authentication.adminJwtPublicKey"
      val AdminJwtPrivateKey = "authentication.adminJwtPrivateKey"
    }

    object Model {
      object Snapshots {
        val Enabled = "model.snapshots.enabled"
        val TriggerByVersion = "model.snapshots.triggerByVersion"
        val LimitedByVersion = "model.snapshots.limitedByVersion"
        val MinVersionInterval = "model.snapshots.minVersionInterval"
        val MaxVersionInterval = "model.snapshots.maxVersionInterval"
        val TriggerByTime = "model.snapshots.triggerByTime"
        val LimitedByTime = "model.snapshots.limitedByTime"
        val MinTimeInterval = "model.snapshots.minTimeInterval"
        val MaxTimeInterval = "model.snapshots.maxTimeInterval"
      }
    }

    object Collection {
      val AutoCreate = "collection.autoCreate"
    }
  }
}
