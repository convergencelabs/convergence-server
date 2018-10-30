package com.convergencelabs.server.datastore.domain

import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ModelSnapshotConfigToODocument
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ODocumentToModelSnapshotConfig
import com.convergencelabs.server.domain.JwtKeyPair
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument

import DomainConfigStore.Fields
import grizzled.slf4j.Logging

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
