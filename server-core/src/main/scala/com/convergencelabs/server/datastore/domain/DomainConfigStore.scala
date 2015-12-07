package com.convergencelabs.server.datastore.domain

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
      val modelDoc: ODocument = doc.field("modelSnapshotConfig", OType.EMBEDDED)
     modelDoc.asModelSnapshotConfig
    }.get
  }

  def setModelSnapshotConfig(modelSnapshotConfig: ModelSnapshotConfig): Try[Unit] = tryWithDb { db =>
    val queryString = "UPDATE DomainConfig SET modelSnapshotConfig = :modelSnapshotConfig"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("modelSnapshotConfig" -> modelSnapshotConfig.asODocument)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    Unit
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

  // FIXME we need an add and remove key.
  def getTokenKey(keyId: String): Try[Option[TokenPublicKey]] = tryWithDb { db =>
    val queryString = "SELECT tokenKeys[id = :keyId].asList() FROM DomainConfig"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("keyId" -> keyId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.mapSingletonListToOption(result) { doc =>
      val keysList: java.util.List[ODocument] = doc.field("keys", OType.EMBEDDEDLIST)
      QueryUtil.mapSingletonList(keysList) { _.asTokenPublicKey }
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
