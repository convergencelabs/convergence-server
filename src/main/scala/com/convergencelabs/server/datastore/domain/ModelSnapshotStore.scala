/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.Date

import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.domain.mapper.ObjectValueMapper.ODocumentToObjectValue
import com.convergencelabs.server.datastore.domain.mapper.ObjectValueMapper.ObjectValueToODocument
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.domain.model.ModelSnapshot
import com.convergencelabs.server.domain.model.ModelSnapshotMetaData
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument

import grizzled.slf4j.Logging

object ModelSnapshotStore {
  import com.convergencelabs.server.datastore.domain.schema.DomainSchema._

  object Constants {
    val CollectionId = "collectionId"
    val ModelId = "modelId"
  }

  def docToModelSnapshot(doc: ODocument): ModelSnapshot = {
    val dataDoc: ODocument = doc.getProperty(Classes.ModelSnapshot.Fields.Data)
    val data = dataDoc.asObjectValue
    ModelSnapshot(docToModelSnapshotMetaData(doc), data)
  }

  def modelSnapshotToDoc(modelSnapshot: ModelSnapshot, db: ODatabaseDocument): Try[ODocument] = {
    val md = modelSnapshot.metaData
    ModelStore.getModelRid(md.modelId, db).flatMap { modelRid =>
      Try {
        val doc: ODocument = db.newInstance(Classes.ModelSnapshot.ClassName)
        doc.setProperty(Classes.ModelSnapshot.Fields.Model, modelRid)
        doc.setProperty(Classes.ModelSnapshot.Fields.Version, modelSnapshot.metaData.version)
        doc.setProperty(Classes.ModelSnapshot.Fields.Timestamp, new Date(modelSnapshot.metaData.timestamp.toEpochMilli()))
        doc.setProperty(Classes.ModelSnapshot.Fields.Data, modelSnapshot.data.asODocument)
        doc
      }
    }
  }

  def docToModelSnapshotMetaData(doc: ODocument): ModelSnapshotMetaData = {
    val timestamp: java.util.Date = doc.getProperty(Classes.ModelSnapshot.Fields.Timestamp)
    ModelSnapshotMetaData(
      doc.getProperty(Constants.ModelId),
      doc.getProperty(Classes.ModelSnapshot.Fields.Version),
      Instant.ofEpochMilli(timestamp.getTime))
  }
  
  /**
   * Removes all snapshot for all models in a collection.
   *
   * @param collectionId The collection id of the collection to remove snapshots for.
   */
  def removeAllSnapshotsForCollection(collectionId: String, db: ODatabaseDocument): Try[Unit] = {
    val command = "DELETE FROM ModelSnapshot WHERE model.collection.id = :collectionId"
    val params = Map(Constants.CollectionId -> collectionId)
    OrientDBUtil.command(db, command, params).map(_ => ())
  }
}

/**
 * Manages the persistence of model snapshots.
 *
 * @constructor Creates a new ModelSnapshotStore using the provided database pool.
 * @param dbPool The database pool to use for connections.
 */
class ModelSnapshotStore private[domain] (
  private[this] val dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import ModelSnapshotStore._
  import com.convergencelabs.server.datastore.domain.schema.DomainSchema._

  val AllFields = s"version, timestamp, model.collection.id as collectionId, model.id as modelId, data"
  val MetaDataFields = s"version, timestamp, model.collection.id as collectionId, model.id as modelId"

  /**
   * Creates a new snapshot in the database.  The combination of modelId and
   * version must be unique in the database.
   *
   * @param snapshotData The snapshot to create.
   */
  def createSnapshot(modelSnapshot: ModelSnapshot): Try[Unit] = withDb { db =>
    modelSnapshotToDoc(modelSnapshot, db)
      .flatMap(doc => Try {
        db.save(doc)
        ()
      })
  }

  /**
   * Gets a snapshot for the specified model and version.
   *
   * @param id The model id of the snapshot to get.
   * @param version The version of the snapshot to get.
   *
   * @return Some(SnapshotData) if a snapshot corresponding to the model and
   * version if it exists, or None if it does not.
   */
  def getSnapshot(id: String, version: Long): Try[Option[ModelSnapshot]] = withDb { db =>
    val query = s"SELECT ${AllFields} FROM ModelSnapshot WHERE model.id = :modelId AND version = :version"
    val params = Map(Constants.ModelId -> id, Classes.ModelSnapshot.Fields.Version -> version)
    OrientDBUtil.findDocumentAndMap(db, query, params)(ModelSnapshotStore.docToModelSnapshot(_))
  }

  /**
   * Gets snapshots for the specified model.
   *
   * @param ud The model id of the snapshot to get.
   * @param version The version of the snapshot to get.
   *
   * @return Some(SnapshotData) if a snapshot corresponding to the model and
   * version if it exists, or None if it does not.
   */
  def getSnapshots(id: String): Try[List[ModelSnapshot]] = withDb { db =>
    val query = s"SELECT ${AllFields} FROM ModelSnapshot WHERE model.id = :modelId"
    val params = Map(Constants.ModelId -> id)
    OrientDBUtil.queryAndMap(db, query, params)(docToModelSnapshot(_))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Meta Data
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Gets a listing of all snapshot meta data for a given model.  The results
   * are sorted in ascending order by version.
   *
   * @param id The id of the model to get the meta data for.
   * @param limit The maximum number of results to return.  If None, then all results are returned.
   * @param offset The offset into the result list.  If None, the default is 0.
   *
   * @return A list of (paged) meta data for the specified model.
   */
  def getSnapshotMetaDataForModel(
    id: String,
    limit: Option[Int],
    offset: Option[Int]): Try[List[ModelSnapshotMetaData]] = withDb { db =>
    val baseQuery =
      s"""SELECT ${MetaDataFields}
        |FROM ModelSnapshot
        |WHERE
        |  model.id = :modelId
        |ORDER BY version ASC""".stripMargin
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    val params = Map(Constants.ModelId -> id)
    OrientDBUtil.queryAndMap(db, query, params)(docToModelSnapshotMetaData(_))
  }

  /**
   * Gets a listing of all snapshot meta data for a given model within time bounds.  The results
   * are sorted in ascending order by version.
   *
   * @param id The id of the model to get the meta data for.
   * @param startTime The lower time bound (defaults to the unix epoc)
   * @param endTime The upper time bound (defaults to now)
   * @param limit The maximum number of results to return.  If None, then all results are returned.
   * @param offset The offest into the result list.  If None, the default is 0.
   *
   * @return A list of (paged) meta data for the specified model.
   */
  def getSnapshotMetaDataForModelByTime(
    id: String,
    startTime: Option[Long],
    endTime: Option[Long],
    limit: Option[Int],
    offset: Option[Int]): Try[List[ModelSnapshotMetaData]] = withDb { db =>
    val baseQuery =
      s"""SELECT ${MetaDataFields}
        |FROM ModelSnapshot
        |WHERE
        |  model.id = :modelId AND
        |  timestamp BETWEEN :startTime AND :endTime
        |ORDER BY version ASC""".stripMargin

    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    val params = Map(
      Constants.ModelId -> id,
      "startTime" -> new java.util.Date(startTime.getOrElse(0L)),
      "endTime" -> new java.util.Date(endTime.getOrElse(Long.MaxValue)))
    OrientDBUtil.queryAndMap(db, query, params)(docToModelSnapshotMetaData(_))
  }

  /**
   * Returns the most recent snapshot meta data for the specified model.
   *
   * @param id The id of the model to get the latest snapshot meta data for.
   *
   * @return the latest snapshot (by version) of the specified model, or None
   * if no snapshots for that model exist.
   */
  def getLatestSnapshotMetaDataForModel(id: String): Try[Option[ModelSnapshotMetaData]] = withDb { db =>
    val query =
      s"""SELECT ${MetaDataFields}
        |FROM ModelSnapshot
        |WHERE
        |  model.id = :modelId
        |ORDER BY version DESC LIMIT 1""".stripMargin
    val params = Map(Constants.ModelId -> id)
    OrientDBUtil.findDocumentAndMap(db, query, params)(docToModelSnapshotMetaData(_))
  }

  def getClosestSnapshotByVersion(id: String, version: Long): Try[Option[ModelSnapshot]] = withDb { db =>
    val query =
      s"""SELECT
        |  abs($$current.version - $version) as abs_delta,
        |  $$current.version - $version as delta,
        |  ${AllFields}
        |FROM ModelSnapshot
        |WHERE
        |  model.id = :modelId
        |ORDER BY
        |  abs_delta ASC,
        |  delta DESC
        |LIMIT 1""".stripMargin
    val params = Map(Constants.ModelId -> id, Classes.ModelSnapshot.Fields.Version -> version)
    OrientDBUtil.findDocumentAndMap(db, query, params)(docToModelSnapshot(_))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Removal
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Removes a snapshot for a model at a specific version.
   *
   * @param id The id of the model to delete the snapshot for.
   * @param version The version of the snapshot to delete.
   */
  def removeSnapshot(id: String, version: Long): Try[Unit] = withDb { db =>
    val command = "DELETE FROM ModelSnapshot WHERE model.id = :modelId AND version = :version"
    val params = Map(Constants.ModelId -> id, Classes.ModelSnapshot.Fields.Version -> version)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }

  /**
   * Removes all snapshots for a model.
   *
   * @param id The id of the model to delete all snapshots for.
   */
  def removeAllSnapshotsForModel(id: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM ModelSnapshot WHERE model.id = :modelId"
    val params = Map(Constants.ModelId -> id)
    OrientDBUtil.command(db, command, params).map(_ => ())
  }
  
  /**
   * Removes all snapshots for a model.
   *
   * @param id The id of the model to delete all snapshots for.
   */
  def removeAllSnapshotsForCollection(collectionId: String): Try[Unit] = withDb { db =>
    ModelSnapshotStore.removeAllSnapshotsForCollection(collectionId, db)
  }
}
