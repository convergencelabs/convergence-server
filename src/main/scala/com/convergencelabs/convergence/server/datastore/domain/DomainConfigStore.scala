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

import com.convergencelabs.convergence.server.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.datastore.domain.DomainConfigStore.Fields
import com.convergencelabs.convergence.server.datastore.domain.mapper.ModelSnapshotConfigMapper.{ModelSnapshotConfigToODocument, ODocumentToModelSnapshotConfig}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.{JwtKeyPair, ModelSnapshotConfig}
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import scala.util.Try

object DomainConfigStore {
  object Fields {
    val ModelSnapshotConfig = "modelSnapshotConfig"
    val AdminPublicKey = "adminPublicKey"
    val AdminPrivateKey = "adminPrivateKey"
    val AnonymousAuth = "anonymousAuth"
  }
}

class DomainConfigStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  def initializeDomainConfig(
    tokenKeyPair: JwtKeyPair,
    modelSnapshotConfig: ModelSnapshotConfig,
    anonymousAuthEnabled: Boolean): Try[Unit] = tryWithDb { db =>
    db.command("DELETE FROM DomainConfig")

    val doc = db.newElement("DomainConfig")
    doc.setProperty(Fields.ModelSnapshotConfig, modelSnapshotConfig.asODocument, OType.EMBEDDED)
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
        configDoc.asModelSnapshotConfig
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
          val configDoc = modelSnapshotConfig.asODocument
          doc.field("modelSnapshotConfig", configDoc)
          doc.save()
        }
      }
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
    val query = """
          |UPDATE
          |  DomainConfig
          |SET
          |  adminPublicKey = :publicKey,
          |  adminPrivateKey = :privateKey""".stripMargin
    val params = Map("publicKey" -> pair.publicKey, "privateKey" -> pair.privateKey)
    OrientDBUtil.mutateOneDocument(db, query, params)
  }
}
