package com.convergencelabs.server.datastore.domain

import java.time.Duration
import java.util.{ List => JavaList }
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.record.OTrackedMap
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import grizzled.slf4j.Logging
import java.util.ArrayList
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.domain.TokenPublicKey
import com.convergencelabs.server.datastore.domain.mapper.TokenPublicKeyMapper._
import com.convergencelabs.server.datastore.domain.mapper.TokenKeyPairMapper._
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper._
import com.convergencelabs.server.domain.ModelSnapshotConfig
import scala.util.Try
import com.convergencelabs.server.util.TryWithResource
import com.convergencelabs.server.domain.TokenKeyPair
import com.convergencelabs.server.domain.TokenKeyPair
import com.convergencelabs.server.datastore.AbstractDatabasePersistence

class DomainConfigStore private[domain] (dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  def getModelSnapshotConfig(): Try[ModelSnapshotConfig] = tryWithDb { db =>
    val queryString = "SELECT modelSnapshotConfig FROM DomainConfig"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val result: JavaList[ODocument] = db.command(query).execute()
    QueryUtil.mapSingleResult(result) { doc =>
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

    QueryUtil.mapSingleResult(result) { doc =>
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

    QueryUtil.flatMapSingleResult(result) { doc =>
      val keysList: java.util.List[ODocument] = doc.field("keys", OType.EMBEDDEDLIST)
      QueryUtil.mapSingleResult(keysList) { _.asTokenPublicKey }
    }
  }

  def getTokenKeys(): Try[Map[String, TokenPublicKey]] = tryWithDb { db =>
    val sql = "SELECT tokenKeys FROM DomainConfig"
    val query = new OSQLSynchQuery[ODocument](sql)
    val result: JavaList[ODocument] = db.command(query).execute()

    QueryUtil.mapSingleResult(result) { doc =>
      val docList: JavaList[ODocument] = doc.field("tokenKeys", OType.EMBEDDEDLIST)
      docList.map(t => {
        val key = t.asTokenPublicKey
        (key.id -> key)
      })(collection.breakOut): Map[String, TokenPublicKey]
    }.get
  }
}