package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.domain.model.ModelFqn
import org.json4s.JsonAST.JValue
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.record.impl.ODocument
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap

class ModelSnapshotStore(dbPool: OPartitionedDatabasePool) {

  def addSnapshot(snapshotData: SnapshotData): Unit = {}

  def removeSnapshot(fqn: ModelFqn, version: Long): Unit = ???

  def removeAllSnapshotsForModel(fqn: ModelFqn): Unit = ???

  def removeAllSnapshotsForCollection(collectionId: String): Unit = ???

  def getSnapshots(fqn: ModelFqn, limit: Option[Int], offset: Option[Int]): List[SnapshotMetaData] = {
    val db = dbPool.acquire()
    
    val limitOffsetString = (limit, offset) match {
      case (None, None)       => ""
      case (Some(_), None)    => " LIMIT :limit"
      case (None, Some(_))    => " SKIP :offset"
      case (Some(_), Some(_)) => " SKIP :offset LIMIT :limit"
    }
    
    val query = new OSQLSynchQuery[ODocument]("SELECT version, timestamp FROM modelSnapshot WHERE collectionId = :collectionId AND modelId = :modelId ORDER BY version ASC" + limitOffsetString)
    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "offset" -> offset.getOrElse(null), "limit" -> limit.getOrElse(null))
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    result.asScala.toList.map { doc => SnapshotMetaData(fqn, doc.field("version"), doc.field("timestamp")) }
  }

  def getSnapshotsByTime(fqn: ModelFqn, startTime: Option[Int], endTime: Option[Int]): List[SnapshotMetaData] = {
//    val db = dbPool.acquire()
//    val query = new OSQLSynchQuery[ODocument]("SELECT version, timestamp FROM modelSnapshot WHERE collectionId = :collectionId AND modelId = :modelId ORDER BY version ASC SKIP :offset LIMIT :limit")
//    val params: java.util.Map[String, Any] = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "offset" -> offset, "limit" -> limit)
//    val result: java.util.List[ODocument] = db.command(query).execute(params)
//    result.asScala.toList.map { doc => SnapshotMetaData(fqn, doc.field("version"), doc.field("timestamp")) }
    ???
  }

  def getSnapshot(fqn: ModelFqn, version: Long): SnapshotData = ???

  def getClosestSnapshotByVersion(fqn: ModelFqn, version: Long): SnapshotData = ???

  def getLatestSnapshotMetaData(fqn: ModelFqn): SnapshotMetaData = ???
}

case class SnapshotMetaData(fqn: ModelFqn, version: Long, timestamp: Long)

case class SnapshotData(metaData: SnapshotMetaData, data: JValue)


