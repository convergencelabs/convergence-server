package com.convergencelabs.server.datastore.domain

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ModelSnapshotConfigToODocument
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ODocumentToModelSnapshotConfig
import com.convergencelabs.server.domain.JwtKeyPair
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

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

class DomainConfigStore (dbProvider: DatabaseProvider)
    extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  def initializeDomainConfig(
      tokenKeyPair: JwtKeyPair, 
      modelSnapshotConfig: ModelSnapshotConfig,
      anonymousAuthEnabled: Boolean): Try[Unit] = tryWithDb { db =>
    db.command(new OCommandSQL("DELETE FROM DomainConfig")).execute()

    val doc = db.newInstance("DomainConfig")
    doc.field(Fields.ModelSnapshotConfig, modelSnapshotConfig.asODocument, OType.EMBEDDED)
    doc.field(Fields.AdminPublicKey, tokenKeyPair.publicKey)
    doc.field(Fields.AdminPrivateKey, tokenKeyPair.privateKey)
    doc.field(Fields.AnonymousAuth, anonymousAuthEnabled)
    db.save(doc)
    ()
  }
  
  def isInitialized(): Try[Boolean] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT count(*) AS count FROM DomainConfig")
    val result: JavaList[ODocument] = db.command(query).execute()
    val count: Long = result.get(0).field("count", OType.LONG)
    count == 1;
  }

  def isAnonymousAuthEnabled(): Try[Boolean] = tryWithDb { db =>
    val queryString = s"SELECT ${Fields.AnonymousAuth} FROM DomainConfig"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val result: JavaList[ODocument] = db.command(query).execute()
    QueryUtil.mapSingletonList(result) { doc =>
      val enabled: Boolean = doc.field(Fields.AnonymousAuth, OType.BOOLEAN)
      enabled
    }.get
  }

  def setAnonymousAuthEnabled(enabled: Boolean): Try[Unit] = tryWithDb { db =>
    val updateString = s"UPDATE DomainConfig SET ${Fields.AnonymousAuth} = :anonymousAuth"
    val query = new OCommandSQL(updateString)
    val params = Map("anonymousAuth" -> enabled)
    val updated: Int = db.command(query).execute(params.asJava)
    require(updated == 1)
    Unit
  }

  def getModelSnapshotConfig(): Try[ModelSnapshotConfig] = tryWithDb { db =>
    val queryString = "SELECT modelSnapshotConfig FROM DomainConfig"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val result: JavaList[ODocument] = db.command(query).execute()
    QueryUtil.mapSingletonList(result) { doc =>
      val configDoc: ODocument = doc.field("modelSnapshotConfig", OType.EMBEDDED)
      configDoc.asModelSnapshotConfig
    }.get
  }

  def setModelSnapshotConfig(modelSnapshotConfig: ModelSnapshotConfig): Try[Unit] = tryWithDb { db =>
    // FIXME why doesn't this work??
    //    val updateString = "UPDATE DomainConfig SET modelSnapshotConfig = :modelSnapshotConfig"
    //    val query = new OCommandSQL(updateString)
    //    val params = Map("modelSnapshotConfig" -> modelSnapshotConfig.asODocument)
    //    val updated: Int = db.command(query).execute(params.asJava)
    //    require(updated == 1)
    //    Unit
    val queryString = "SELECT FROM DomainConfig"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val result: JavaList[ODocument] = db.command(query).execute()
    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        val configDoc = modelSnapshotConfig.asODocument
        doc.field("modelSnapshotConfig", configDoc)
        doc.save()
        Unit
      case None =>
        throw new IllegalStateException("DomainConfig not found")
    }
  }

  def getAdminKeyPair(): Try[JwtKeyPair] = tryWithDb { db =>
    val queryString = "SELECT adminPublicKey, adminPrivateKey FROM DomainConfig"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val result: JavaList[ODocument] = db.command(query).execute()

    QueryUtil.mapSingletonList(result) { doc =>
      JwtKeyPair(
        doc.field("adminPublicKey", OType.STRING),
        doc.field("adminPrivateKey", OType.STRING))
    }.get
  }

  def setAdminKeyPair(pair: JwtKeyPair): Try[Unit] = tryWithDb { db =>
    val queryString = """
      |UPDATE
      |  DomainConfig
      |SET
      |  adminPublicKey = :publicKey, 
      |  adminPrivateKey = :privateKey""".stripMargin
    val command = new OCommandSQL(queryString)

    val params = Map("publicKey" -> pair.publicKey, "privateKey" -> pair.privateKey).asJava
    db.command(command).execute(params)
    ()
  }
}
