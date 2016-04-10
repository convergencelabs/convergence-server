package com.convergencelabs.server.datastore.domain

import java.util.Collections
import java.util.{ List => JavaList }

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ModelSnapshotConfigToODocument
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ODocumentToModelSnapshotConfig
import com.convergencelabs.server.datastore.domain.mapper.TokenPublicKeyMapper.ODocumentToTokenPublicKey
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.TokenKeyPair
import com.convergencelabs.server.domain.TokenPublicKey
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import grizzled.slf4j.Logging

class DomainConfigStore private[domain] (dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

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

  def getAdminKeyPair(): Try[TokenKeyPair] = tryWithDb { db =>
    val queryString = "SELECT adminPublicKey, adminPrivateKey FROM DomainConfig"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val result: JavaList[ODocument] = db.command(query).execute()

    QueryUtil.mapSingletonList(result) { doc =>
      TokenKeyPair(
        doc.field("adminPublicKey", OType.STRING),
        doc.field("adminPrivateKey", OType.STRING))
    }.get
  }

  // FIXME we need an add and remove tokenKey.

  def getTokenKey(keyId: String): Try[Option[TokenPublicKey]] = tryWithDb { db =>
    val queryString = "SELECT tokenKeys[id = :keyId] FROM DomainConfig"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("keyId" -> keyId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.mapSingletonListToOption(result) { doc =>
      val keys: JavaList[ODocument] = Collections.singletonList(doc.field("tokenKeys"))
      QueryUtil.mapSingletonList(keys) { _.asTokenPublicKey }
    }
  }

  def getTokenKeys(): Try[Map[String, TokenPublicKey]] = tryWithDb { db =>
    val sql = "SELECT tokenKeys FROM DomainConfig"
    val query = new OSQLSynchQuery[ODocument](sql)
    val result: JavaList[ODocument] = db.command(query).execute()

    QueryUtil.mapSingletonList(result) { doc =>
      val docList: JavaList[ODocument] = doc.field("tokenKeys", OType.EMBEDDEDLIST)
      docList.map(t => {
        val key = t.asTokenPublicKey
        (key.id -> key)
      })(collection.breakOut): Map[String, TokenPublicKey]
    }.get
  }
}
