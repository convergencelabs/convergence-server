package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.orientechnologies.orient.core.metadata.schema.OType
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._
import scala.collection.immutable.HashMap
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.convergencelabs.server.domain.model.ot.ops.Operation
import com.convergencelabs.server.datastore.domain.ModelOperationStore.Fields._
import java.time.Instant

object ModelOperationStore {
  val ModelOperation = "ModelOperation"
  
  object Fields {
    val Version = "version"
    val ModelId = "modelId"
    val CollectionId = "collectionId"
    val Timestamp = "collectionId"
    val Uid = "uid"
    val Cid = "cid"
    val Operation = "op"
  }
}

class ModelOperationStore(dbPool: OPartitionedDatabasePool) {
  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def getMaxVersion(fqn: ModelFqn): Option[Long] = {
    val db = dbPool.acquire()
    val queryString =
      """SELECT max(operation.version) 
        |FROM ModelOperation 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    
    db.close()
    
    result.asScala.toList match {
      case doc :: Nil => Some(doc.field("max", OType.LONG))
      case _ => None
    }
  }

  def getVersionAtOrBeforeTime(fqn: ModelFqn, time: Long): Option[Long] = {
    val db = dbPool.acquire()
    val queryString = 
      """SELECT max(operation.version) 
        |FROM ModelOperation 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId AND 
        |  operation.time <= :time""".stripMargin
    
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "time" -> time)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList match {
      case doc :: rest => Some(doc.field("max", OType.LONG))
      case Nil => None
    }
  }

  def getOperationsAfterVersion(fqn: ModelFqn, version: Long): List[OperationEvent] = {
    val db = dbPool.acquire()
    val queryString = 
      """SELECT * FROM ModelOperation 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId AND 
        |  operation.version >= :version 
        |ORDER BY operation.version ASC""".stripMargin
        
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "version" -> version)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList map { doc => docToOperationEvent(doc) }
  }

  def getOperationsAfterVersion(fqn: ModelFqn, version: Long, limit: Int): List[OperationEvent] = {
    val db = dbPool.acquire()
    val queryStirng = 
      """SELECT * FROM ModelOperation 
        |WHERE 
        |  collectionId = :collectionId AND
        |  modelId = :modelId AND
        | operation.version >= :version
        |ORDER BY operation.version ASC LIMIT :limit""".stripMargin
        
    val query = new OSQLSynchQuery[ODocument](queryStirng)
    val params = HashMap(
        "collectionId" -> fqn.collectionId, 
        "modelId" -> fqn.modelId, 
        "version" -> version, 
        "limit" -> limit)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList map { doc => docToOperationEvent(doc) }
  }

  def removeHistoryForModel(fqn: ModelFqn): Unit = {
    val db = dbPool.acquire()
    val queryStirng = 
      """DELETE FROM ModelOperation 
        |WHERE 
        |  collectionId = :collectionId AND
        |  modelId = :modelId""".stripMargin
        
    val query = new OSQLSynchQuery[ODocument](queryStirng)
    val params = HashMap(
        "collectionId" -> fqn.collectionId, 
        "modelId" -> fqn.modelId)
    val command = new OCommandSQL("DELETE FROM User WHERE uid = :uid")
    db.command(command).execute(params.asJava)
    db.close()
  }
  
  private[this] def docToOperationEvent(doc: ODocument): OperationEvent = {
    val docDate: java.util.Date = doc.field(Timestamp, OType.DATETIME)
    val timestamp = Instant.ofEpochMilli(docDate.getTime)
    val opMap: java.util.Map[String, Object] = doc.field(Operation, OType.EMBEDDEDMAP)
    
    val op = OrientDBOperationMapper.mapToOperation(opMap)
    
    return OperationEvent(
        ModelFqn(doc.field(CollectionId), doc.field(ModelId)),
        doc.field(Version),
        timestamp,
        doc.field(Uid),
        doc.field(Cid),
        op)
  }
}

case class OperationEvent(
    modelFqn: ModelFqn, 
    version: Long, 
    timestamp: Instant, 
    uid: String,
    cid: String,
    op: Operation)