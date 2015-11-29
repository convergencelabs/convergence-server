package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.immutable.HashMap
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelSnapshot
import com.convergencelabs.server.domain.model.ModelSnapshotMetaData
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import mapper.ModelSnapshotMapper._

import grizzled.slf4j.Logging

/**
 * Manages the persistence of model snapshots.
 *
 * @constructor Creates a new ModelSnapshotStore using the provided database pool.
 * @param dbPool The database pool to use for connections.
 */
class ModelSnapshotStore private[domain] (
  private[this] val dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  private[this] implicit val formats = org.json4s.DefaultFormats

  /**
   * Creates a new snapshot in the database.  The combination of ModelFqn and
   * version must be unique in the database.
   *
   * @param snapshotData The snapshot to create.
   */
  def createSnapshot(modelSnapshot: ModelSnapshot): Try[Unit] = tryWithDb { db =>
    db.save(modelSnapshot.asODocument)
    Unit
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
  def getSnapshot(fqn: ModelFqn, version: Long): Try[Option[ModelSnapshot]] = tryWithDb { db =>
    val queryString =
      """SELECT *
        |FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId AND
        |  version = :version""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = HashMap(
      "collectionId" -> fqn.collectionId,
      "modelId" -> fqn.modelId,
      "version" -> version)

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingleResult(result) { _.asModelSnapshot }
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
  def getSnapshotMetaDataForModel(
    fqn: ModelFqn,
    limit: Option[Int],
    offset: Option[Int]): Try[List[ModelSnapshotMetaData]] = tryWithDb { db =>
    val baseQuery =
      """SELECT collectionId, modelId, version, timestamp 
        |FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId
        |ORDER BY version ASC""".stripMargin

    val query = new OSQLSynchQuery[ODocument](QueryUtil.buildPagedQuery(baseQuery, limit, offset))
    val params = HashMap(
      "collectionId" -> fqn.collectionId,
      "modelId" -> fqn.modelId)

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList.map { _.asModelSnapshotMetaData }
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
    offset: Option[Int]): Try[List[ModelSnapshotMetaData]] = tryWithDb { db =>
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
      "startTime" -> new java.util.Date(startTime.getOrElse(0L)),
      "endTime" -> new java.util.Date(endTime.getOrElse(Long.MaxValue)))

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList.map { _.asModelSnapshotMetaData }
  }

  /**
   * Returns the most recent snapshot meta data for the specified model.
   *
   * @param fqn The ModelFqn of the model to get the latest snapshot meta data for.
   *
   * @return the latest snapshot (by version) of the specified model, or None
   * if no snapshots for that model exist.
   */
  def getLatestSnapshotMetaDataForModel(fqn: ModelFqn): Try[Option[ModelSnapshotMetaData]] = tryWithDb { db =>
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
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingleResult(result) { _.asModelSnapshotMetaData }
  }

  def getClosestSnapshotByVersion(fqn: ModelFqn, version: Long): Try[Option[ModelSnapshot]] = tryWithDb { db =>
    val queryString =
      s"""SELECT 
        |  abs(eval('$$current.version - $version')) as abs_delta, 
        |  eval('$$current.version - $version') as delta, 
        |  * 
        |FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId 
        |ORDER BY 
        |  abs_delta ASC, 
        |  delta DESC 
        |LIMIT 1""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = HashMap(
      "collectionId" -> fqn.collectionId,
      "modelId" -> fqn.modelId,
      "version" -> version)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingleResult(result) { _.asModelSnapshot }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Removal
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Removes a snapshot for a model at a specific version.
   *
   * @param fqn The ModelFqn of the model to delete the snapshot for.
   * @param version The version of the snapshot to delete.
   */
  def removeSnapshot(fqn: ModelFqn, version: Long): Try[Unit] = tryWithDb { db =>
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
    Unit
  }

  /**
   * Removes all snapshots for a model.
   *
   * @param fqn The ModelFqn of the model to delete all snapshots for.
   */
  def removeAllSnapshotsForModel(fqn: ModelFqn): Try[Unit] = tryWithDb { db =>
    val query =
      """DELETE FROM ModelSnapshot 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId""".stripMargin

    val command = new OCommandSQL(query);
    val params = HashMap("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    db.command(command).execute(params.asJava)
    Unit
  }

  /**
   * Removes all snapshot for all models in a collection.
   *
   * @param collectionId The collection id of the collection to remove snapshots for.
   */
  def removeAllSnapshotsForCollection(collectionId: String): Try[Unit] = tryWithDb { db =>
    val query = "DELETE FROM ModelSnapshot WHERE collectionId = :collectionId"

    val command = new OCommandSQL(query);
    val params = HashMap("collectionId" -> collectionId)
    db.command(command).execute(params.asJava)
    Unit
  }
}