package com.convergencelabs.server.datastore.orient

import com.convergencelabs.server.datastore.domain.ModelHistoryStore
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.datastore.domain.OperationEvent
import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.orientechnologies.orient.core.metadata.schema.OType
import com.convergencelabs.server.datastore.domain.OperationEvent
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._

class OrientDBModelHistoryStore(dbPool: OPartitionedDatabasePool) extends ModelHistoryStore {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def getMaxVersion(fqn: ModelFqn): Long = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT max(operation.version) FROM modelHistory WHERE collectionId = :collectionId and modelId = :modelId")
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList match {
      case doc :: rest => doc.field("max", OType.LONG)
      case Nil         => 0 //TODO: should we be using an option for this
    }
  }

  def getVersionAtOrBeforeTime(fqn: ModelFqn, time: Long): Long = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT max(operation.version) FROM modelHistory WHERE collectionId = :collectionId and modelId = :modelId and operation.time <= :time")
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "time" -> time)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList match {
      case doc :: rest => doc.field("max", OType.LONG)
      case Nil         => 0 //TODO: should we be using an option for this
    }
  }

  def getOperationsAfterVersion(fqn: ModelFqn, version: Long): List[OperationEvent] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM modelHistory WHERE collectionId = :collectionId and modelId = :modelId and operation.version >= :version ORDER BY operation.version ASC");
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "version" -> version)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList map { doc => read[OperationEvent](write(doc.field("operation"))) }
  }

  def getOperationsAfterVersion(fqn: ModelFqn, version: Long, limit: Int): List[OperationEvent] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM modelHistory WHERE collectionId = :collectionId and modelId = :modelId and operation.version >= :version ORDER BY operation.version ASC LIMIT :limit");
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "version" -> version, "limit" -> limit)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList map { doc => read[OperationEvent](write(doc.field("operation"))) }
  }

  def removeHistoryForModel(modelFqn: ModelFqn): Unit = ???
}