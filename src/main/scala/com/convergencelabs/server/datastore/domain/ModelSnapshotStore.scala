package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.domain.model.ModelFqn
import org.json4s.JsonAST.JValue
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.record.impl.ODocument
import scala.collection.JavaConverters._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.orientechnologies.orient.core.sql.OCommandSQL
import scala.collection.immutable.HashMap
import com.convergencelabs.server.datastore.QueryUtil
import scala.compat.Platform
import com.orientechnologies.orient.core.metadata.schema.OType

/**
 * Manages the persistence of model snapshots.
 *
 * @constructor Creates a new ModelSnapshotStore using the provided database pool.
 * @param dbPool The database pool to use for connections.
 */
class ModelSnapshotStore private[domain] (
    private[this] val dbPool: OPartitionedDatabasePool) {

  private[this] implicit val formats = org.json4s.DefaultFormats

  /**
   * Creates a new snapshot in the database.  The combination of ModelFqn and
   * version must be unique in the database.
   *
   * @param snapshotData The snapshot to create.
   */
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

  /**
   * Gets a snapshot for the specified model and version.
   *
   * @param fqn The model identifier of the snapshot to get.
   * @param version The version of the snapshot to get.
   *
   * @return Some(SnapshotData) if a snapshot corresponding to the model and
   * version if it exists, or None if it does not.
   */
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
      case _ => throw new RuntimeException() // FIXME
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Meta Data
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Gets a listing of all snapshot meta data for a given model.  The results
   * are sorted in ascending order by version.
   *
   * @param fqn the ModelFqn of the model to get the meta data for.
   * @param limit The maximum number of results to return.  If None, then all results are returned.
   * @param offset The offest into the result list.  If None, the default is 0.
   *
   * @return A list of (paged) meta data for the specified model.
   */
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

  /**
   * Gets a listing of all snapshot meta data for a given model within time bounds.  The results
   * are sorted in ascending order by version.
   *
   * @param fqn the ModelFqn of the model to get the meta data for.
   * @param startTime The lower time bound (defaults to the unix epoc)
   * @param endTime The upper time bound (defaults to now)
   * @param limit The maximum number of results to return.  If None, then all results are returned.
   * @param offset The offest into the result list.  If None, the default is 0.
   *
   * @return A list of (paged) meta data for the specified model.
   */
  def getSnapshotMetaDataForModelByTime(
    fqn: ModelFqn,
    startTime: Option[Long],
    endTime: Option[Long],
    limit: Option[Int],
    offset: Option[Int]): List[SnapshotMetaData] = {

    val db = dbPool.acquire()
    val baseQuery =
      """SELECT version, timestamp 
        |FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId AND
        |  timestamp BETWEEN :startTime AND :endTime
        |ORDER BY version ASC""".stripMargin

    val query = new OSQLSynchQuery[ODocument](QueryUtil.buildPagedQuery(baseQuery, limit, offset))
    val params = HashMap(
      "collectionId" -> fqn.collectionId,
      "modelId" -> fqn.modelId,
      "startTime" -> startTime.getOrElse(0),
      "endTime" -> endTime.getOrElse(Platform.currentTime))

    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList.map { doc => convertDocToSnapshotMetaData(doc) }
  }

  /**
   * Returns the most recent snapshot meta data for the specified model.
   *
   * @param fqn The ModelFqn of the model to get the latest snapshot meta data for.
   *
   * @return the latest snapshot (by version) of the specified model, or None
   * if no snapshots for that model exist.
   */
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

  /**
   * Removes a snapshot for a model at a specific version.
   *
   * @param fqn The ModelFqn of the model to delete the snapshot for.
   * @param version The version of the snapshot to delete.
   */
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

  /**
   * Removes all snapshots for a model.
   *
   * @param fqn The ModelFqn of the model to delete all snapshots for.
   */
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

  /**
   * Removes all snapshot for all models in a collection.
   *
   * @param collectionId The collection id of the collection to remove snapshots for.
   */
  def removeAllSnapshotsForCollection(collectionId: String): Unit = {
    val db = dbPool.acquire()
    val query = "DELETE FROM ModelSnapshot WHERE collectionId = :collectionId"

    val command = new OCommandSQL(query);
    val params = HashMap("collectionId" -> collectionId)
    db.command(command).execute(params.asJava)
    db.close()
  }

  /**
   * A helper method to convert an ODocument to a SnapshotMetaData object.
   */
  private[this] def convertDocToSnapshotMetaData(doc: ODocument): SnapshotMetaData = {
    val timestamp: java.util.Date = doc.field("timestamp")
    SnapshotMetaData(
      ModelFqn(doc.field("collectionId"), doc.field("modelId")),
      doc.field("version", OType.LONG),
      timestamp.getTime)
  }

  /**
   * A helper method to convert an ODocument to a SnapshotData object.
   */
  private[this] def convertDocToSnapshotData(doc: ODocument): SnapshotData = {
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