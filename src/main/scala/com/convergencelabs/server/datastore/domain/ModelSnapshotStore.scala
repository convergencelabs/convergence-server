package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.domain.model.ModelFqn
import org.json4s.JsonAST.JValue
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.record.impl.ODocument
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.orientechnologies.orient.core.sql.OCommandSQL
import scala.collection.immutable.HashMap

class ModelSnapshotStore(dbPool: OPartitionedDatabasePool) {
  private[this] implicit val formats = org.json4s.DefaultFormats

  def addSnapshot(snapshotData: SnapshotData): Unit = {
    val db = dbPool.acquire()
    val doc = db.newInstance("ModelSnapshot")
    doc.field("modelId", snapshotData.metaData.fqn.modelId)
    doc.field("collectionId", snapshotData.metaData.fqn.collectionId)
    doc.field("version", snapshotData.metaData.version)
    doc.field("timestamp", snapshotData.metaData.timestamp)
    // NOTE: Extracting to a map to avoid string serialization
    doc.field("data", snapshotData.data.extract[Map[String, _]])
    db.save(doc)
    db.close()
  }

  def removeSnapshot(fqn: ModelFqn, version: Long): Unit = {
    val db = dbPool.acquire()
    // NOTE: Multi-line strings
    val query = 
      """DELETE FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId AND 
        |  version = :version""".stripMargin

    val command = new OCommandSQL(query);

    // NOTE: don't need to convert to the Java Map here, cause it will be
    // auto converted below.
    val params = HashMap(
      "collectionId" -> fqn.collectionId,
      "modelId" -> fqn.modelId,
      "version" -> version)

    db.command(command).execute(params)
    // NOTE: We don't seem to call close in the ModelStore.
    db.close()
  }

  def removeAllSnapshotsForModel(fqn: ModelFqn): Unit = {
    val db = dbPool.acquire()
    val query = 
      """DELETE FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId""".stripMargin

    val command = new OCommandSQL(query);
    val params = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    db.command(command).execute(params)
    db.close()
  }

  def removeAllSnapshotsForCollection(collectionId: String): Unit = {
    val db = dbPool.acquire()
    val query = "DELETE FROM ModelSnapshot WHERE collectionId = :collectionId"

    val command = new OCommandSQL(query);
    val params = HashMap("collectionId" -> collectionId)
    db.command(command).execute(params)
    db.close()
  }

  def getSnapshots(fqn: ModelFqn, limit: Option[Int], offset: Option[Int]): List[SnapshotMetaData] = {
    val db = dbPool.acquire()
    val baseQuery = 
      """SELECT version, timestamp 
        |FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId
        |ORDER BY version ASC""".stripMargin
                      
    val query = new OSQLSynchQuery[ODocument](buildPagedQuery(baseQuery, limit, offset))
    val params = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "offset" -> offset.getOrElse(null), "limit" -> limit.getOrElse(null))
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    result.asScala.toList.map { doc => convertDocToSnapshotMetaData(doc) }
  }

  def getSnapshotsByTime(fqn: ModelFqn, startTime: Option[Int], endTime: Option[Int]): List[SnapshotMetaData] = {
    val db = dbPool.acquire()
    val queryString = 
      """SELECT version, timestamp 
        |FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId AND
        |  timestamp BETWEEN :startTime AND :endTime
        |ORDER BY version ASC""".stripMargin
                      
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = HashMap(
        "collectionId" -> fqn.collectionId, 
        "modelId" -> fqn.modelId,
        "startTime" -> startTime.getOrElse(null),
        "endTime" -> endTime.getOrElse(null))
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    result.asScala.toList.map { doc => convertDocToSnapshotMetaData(doc) }
  }

  def getSnapshot(fqn: ModelFqn, version: Long): Option[SnapshotData] = {
    val db = dbPool.acquire()
    val queryString = 
      """SELECT version, timestamp 
        |FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId AND
        |  version = :version
        |ORDER BY version ASC""".stripMargin
                      
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = HashMap(
        "collectionId" -> fqn.collectionId, 
        "modelId" -> fqn.modelId, 
        "version" -> version)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    result.asScala.toList match {
      case doc :: rest => Some(convertDocToSnapshotData(doc))
      case Nil         => None
    }
  }

  def getLatestSnapshotMetaData(fqn: ModelFqn): Option[SnapshotMetaData] = {
    val db = dbPool.acquire()
    val queryString = 
      """SELECT version, timestamp 
        |FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId AND
        |  version = :version
        |ORDER BY version DESC LIMIT 1""".stripMargin
                      
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = HashMap(
        "collectionId" -> fqn.collectionId, 
        "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    result.asScala.toList match {
      case doc :: rest => Some(convertDocToSnapshotMetaData(doc))
      case Nil         => None
    }
  }
  
  def getClosestSnapshotByVersion(fqn: ModelFqn, version: Long): Option[SnapshotData] = ??? // FIXME

  // FIXE abstract this to a utility method
  def buildPagedQuery(baseQuery: String, limit: Option[Int], offset: Option[Int]): String = {
    val limitOffsetString = (limit, offset) match {
      case (None, None) => ""
      case (Some(_), None) => " LIMIT :limit"
      case (None, Some(_)) => " SKIP :offset"
      case (Some(_), Some(_)) => " SKIP :offset LIMIT :limit"
    }

    baseQuery + limitOffsetString
  }
  
  private def convertDocToSnapshotMetaData(doc: ODocument): SnapshotMetaData = {
    SnapshotMetaData(
        ModelFqn(doc.field("modelId"), doc.field("collectionId")),
        doc.field("version"), 
        doc.field("timestamp"))
  }
  
  private def convertDocToSnapshotData(doc: ODocument): SnapshotData = {
    // NOTE: Need to test this.  I think it works.
    val dataMap: java.util.Map[String, Object] = doc.field("data")
    val data = Extraction.decompose(dataMap)
    
    SnapshotData(
      SnapshotMetaData(
        ModelFqn(doc.field("modelId"), doc.field("collectionId")),
        doc.field("version"), 
        doc.field("timestamp")),
        data)
  }
}

case class SnapshotMetaData(fqn: ModelFqn, version: Long, timestamp: Long)
case class SnapshotData(metaData: SnapshotMetaData, data: JValue)