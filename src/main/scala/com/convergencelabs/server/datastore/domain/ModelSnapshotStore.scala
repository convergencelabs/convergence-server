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
import com.convergencelabs.server.datastore.QueryUtil

class ModelSnapshotStore private[domain] (
    private[this] val dbPool: OPartitionedDatabasePool) {
  
  private[this] implicit val formats = org.json4s.DefaultFormats

  def createSnapshot(snapshotData: SnapshotData): Unit = {
    val db = dbPool.acquire()
    val doc = db.newInstance("ModelSnapshot")
    doc.field("modelId", snapshotData.metaData.fqn.modelId)
    doc.field("collectionId", snapshotData.metaData.fqn.collectionId)
    doc.field("version", snapshotData.metaData.version)
    doc.field("timestamp", new java.util.Date(snapshotData.metaData.timestamp))
    // NOTE: Extracting to a map to avoid string serialization
    val data = snapshotData.data.extract[Map[String, _]]
    doc.field("data", data.asJava)
    db.save(doc)
    db.close()
  }

  def getSnapshot(fqn: ModelFqn, version: Long): Option[SnapshotData] = {
    val db = dbPool.acquire()

    // NOTE: Multi-line strings
    val queryString =
      """SELECT *
        |FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId AND
        |  version = :version""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    // NOTE: don't need to convert to the Java Map here, cause it will be
    // auto converted below.
    val params = HashMap(
      "collectionId" -> fqn.collectionId,
      "modelId" -> fqn.modelId,
      "version" -> version)

    // NOTE: This is where we convert to a java map.
    // ?? Why don't we just use a java map?
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)

    // NOTE: Calling close?  I don't think  we were doing this.
    db.close()

    // NOTE: Match doc :: Nil to only match a list with one element.  
    // 1 element is ok, none is ok, more than one element is not ok.
    // database should enforce this, but I think we should check since
    // it is so easy.  Or we could not check.  But either way we should
    // match doc :: Nil since that is what we actually want
    result.asScala.toList match {
      case doc :: Nil => Some(convertDocToSnapshotData(doc))
      case Nil => None
      case doc :: rest => throw new RuntimeException() // FIXME
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Meta Data
  /////////////////////////////////////////////////////////////////////////////

  def getSnapshotMetaDataForModel(fqn: ModelFqn, limit: Option[Int], offset: Option[Int]): List[SnapshotMetaData] = {
    val db = dbPool.acquire()
    val baseQuery =
      """SELECT version, timestamp 
        |FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId
        |ORDER BY version ASC""".stripMargin

    val query = new OSQLSynchQuery[ODocument](QueryUtil.buildPagedQuery(baseQuery, limit, offset))
    val params = HashMap(
      "collectionId" -> fqn.collectionId,
      "modelId" -> fqn.modelId,
      "offset" -> offset.getOrElse(null),
      "limit" -> limit.getOrElse(null))

    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList.map { doc => convertDocToSnapshotMetaData(doc) }
  }

  def getSnapshotMetaDataForModelByTime(fqn: ModelFqn, startTime: Option[Int], endTime: Option[Int]): List[SnapshotMetaData] = {
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
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList.map { doc => convertDocToSnapshotMetaData(doc) }
  }

  def getLatestSnapshotMetaDataForModel(fqn: ModelFqn): Option[SnapshotMetaData] = {
    val db = dbPool.acquire()
    val queryString =
      """SELECT version, timestamp 
        |FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId
        |ORDER BY version DESC LIMIT 1""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = HashMap(
      "collectionId" -> fqn.collectionId,
      "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList match {
      case doc :: Nil => Some(convertDocToSnapshotMetaData(doc))
      case Nil => None
      case _ => ??? // FIXME
    }
  }

  def getClosestSnapshotByVersion(fqn: ModelFqn, version: Long): Option[SnapshotData] = ??? // FIXME

  /////////////////////////////////////////////////////////////////////////////
  // Removal
  /////////////////////////////////////////////////////////////////////////////

  def removeSnapshot(fqn: ModelFqn, version: Long): Unit = {
    val db = dbPool.acquire()
    val query =
      """DELETE FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId AND 
        |  version = :version""".stripMargin

    val command = new OCommandSQL(query);
    val params = HashMap(
      "collectionId" -> fqn.collectionId,
      "modelId" -> fqn.modelId,
      "version" -> version)

    db.command(command).execute(params.asJava)
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
    db.command(command).execute(params.asJava)
    db.close()
  }

  def removeAllSnapshotsForCollection(collectionId: String): Unit = {
    val db = dbPool.acquire()
    val query = "DELETE FROM ModelSnapshot WHERE collectionId = :collectionId"

    val command = new OCommandSQL(query);
    val params = HashMap("collectionId" -> collectionId)
    db.command(command).execute(params.asJava)
    db.close()
  }

  private def convertDocToSnapshotMetaData(doc: ODocument): SnapshotMetaData = {
    val timestamp: java.util.Date = doc.field("timestamp")
    SnapshotMetaData(
      ModelFqn(doc.field("collectionId"), doc.field("modelId")),
      doc.field("version"),
      timestamp.getTime)
  }

  private def convertDocToSnapshotData(doc: ODocument): SnapshotData = {
    val dataMap: java.util.Map[String, Object] = doc.field("data")
    val data = Extraction.decompose(dataMap.asScala)
    val timestamp: java.util.Date = doc.field("timestamp")

    SnapshotData(
      SnapshotMetaData(
        ModelFqn(doc.field("collectionId"), doc.field("modelId")),
        doc.field("version"),
        timestamp.getTime),
      data)
  }
}

case class SnapshotMetaData(fqn: ModelFqn, version: Long, timestamp: Long)
case class SnapshotData(metaData: SnapshotMetaData, data: JValue)