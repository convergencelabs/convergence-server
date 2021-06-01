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

import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ModelSnapshotConfigMapper.{modelSnapshotConfigToODocument, oDocumentToModelSnapshotConfig}
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.domain.jwt.JwtKeyPair
import com.convergencelabs.convergence.server.model.domain.{CollectionConfig, ModelSnapshotConfig}
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import scala.util.Try

class DomainConfigStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import com.convergencelabs.convergence.server.backend.datastore.domain.schema.DomainConfigClass._

  def initializeDomainConfig(tokenKeyPair: JwtKeyPair,
                             collectionConfig: CollectionConfig,
                             modelSnapshotConfig: ModelSnapshotConfig,
                             anonymousAuthEnabled: Boolean): Try[Unit] = tryWithDb { db =>
    db.command("DELETE FROM DomainConfig")

    val doc = db.newElement("DomainConfig")
    doc.setProperty(Fields.ModelSnapshotConfig, modelSnapshotConfigToODocument(modelSnapshotConfig), OType.EMBEDDED)
    doc.setProperty(Fields.CollectionConfig, DomainConfigStore.collectionConfigToODocument(collectionConfig), OType.EMBEDDED)
    doc.setProperty(Fields.AdminPublicKey, tokenKeyPair.publicKey)
    doc.setProperty(Fields.AdminPrivateKey, tokenKeyPair.privateKey)
    doc.setProperty(Fields.AnonymousAuth, anonymousAuthEnabled)
    db.save(doc)
    ()
  }

  def isInitialized(): Try[Boolean] = withDb { db =>
    OrientDBUtil
      .getDocument(db, "SELECT count(*) AS count FROM DomainConfig")
      .map(_.getProperty("count").asInstanceOf[Long] == 1)
  }

  def isAnonymousAuthEnabled(): Try[Boolean] = withDb { db =>
    val query = s"SELECT ${Fields.AnonymousAuth} FROM DomainConfig"
    OrientDBUtil
      .getDocument(db, query)
      .flatMap(doc => Try {
        val anonymouAuth: Boolean = doc.getProperty(Fields.AnonymousAuth)
        anonymouAuth
      })
  }

  def setAnonymousAuthEnabled(enabled: Boolean): Try[Unit] = tryWithDb { db =>
    val query = s"UPDATE DomainConfig SET ${Fields.AnonymousAuth} = :anonymousAuth"
    val params = Map("anonymousAuth" -> enabled)
    OrientDBUtil
      .mutateOneDocument(db, query, params)
  }

  def getModelSnapshotConfig(): Try[ModelSnapshotConfig] = withDb { db =>
    val query = "SELECT modelSnapshotConfig FROM DomainConfig"
    OrientDBUtil
      .getDocument(db, query)
      .map { doc =>
        val configDoc: ODocument = doc.field("modelSnapshotConfig", OType.EMBEDDED)
        oDocumentToModelSnapshotConfig(configDoc)
      }
  }

  def setModelSnapshotConfig(modelSnapshotConfig: ModelSnapshotConfig): Try[Unit] = tryWithDb { db =>
    // FIXME why doesn't this work??
    //    val updateString = "UPDATE DomainConfig SET modelSnapshotConfig = :modelSnapshotConfig"
    //    val query = new OCommandSQL(updateString)
    //    val params = Map("modelSnapshotConfig" -> modelSnapshotConfig.asODocument)
    //    val updated: Int = db.command(query).execute(params.asJava)
    //    require(updated == 1)
    //    Unit

    val query = "SELECT FROM DomainConfig"
    OrientDBUtil
      .getDocument(db, query)
      .flatMap { doc =>
        Try {
          val configDoc = modelSnapshotConfigToODocument(modelSnapshotConfig)
          doc.field(Fields.ModelSnapshotConfig, configDoc)
          doc.save()
        }
      }
  }

  def getCollectionConfig(): Try[CollectionConfig] = withDb { db =>
    val query = "SELECT collectionConfig FROM DomainConfig"
    OrientDBUtil
      .getDocument(db, query)
      .map { doc =>
        val configDoc: ODocument = doc.field(Fields.CollectionConfig, OType.EMBEDDED)
        DomainConfigStore.docToCollectionConfig(configDoc)
      }
  }

  def setCollectionConfig(config: CollectionConfig): Try[Unit] = tryWithDb { db =>
    val command = "UPDATE DomainConfig SET collectionConfig.autoCreate = :autoCreate"
    OrientDBUtil.command(db, command, Map("autoCreate" -> config.autoCreate))
  }

  def getAdminKeyPair(): Try[JwtKeyPair] = withDb { db =>
    val query = "SELECT adminPublicKey, adminPrivateKey FROM DomainConfig"
    OrientDBUtil
      .getDocument(db, query)
      .map { doc =>
        JwtKeyPair(
          doc.field("adminPublicKey", OType.STRING),
          doc.field("adminPrivateKey", OType.STRING))
      }
  }

  def setAdminKeyPair(pair: JwtKeyPair): Try[Unit] = withDb { db =>
    val query =
      """
        |UPDATE
        |  DomainConfig
        |SET
        |  adminPublicKey = :publicKey,
        |  adminPrivateKey = :privateKey""".stripMargin
    val params = Map("publicKey" -> pair.publicKey, "privateKey" -> pair.privateKey)
    OrientDBUtil.mutateOneDocument(db, query, params)
  }
}

object DomainConfigStore {
  def collectionConfigToODocument(config: CollectionConfig): ODocument = {
    val doc = new ODocument("CollectionConfig")
    doc.setProperty("autoCreate", config.autoCreate)
    doc
  }

  def docToCollectionConfig(doc: ODocument): CollectionConfig = {
    val autoCreate = doc.getProperty("autoCreate").asInstanceOf[Boolean]
    CollectionConfig(autoCreate)
  }
}
