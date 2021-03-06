/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain.model

import java.time.Instant
import java.util.Date

import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectValueMapper.{ODocumentToObjectValue, ObjectValueToODocument}
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.domain.model
import com.convergencelabs.convergence.server.model.domain.model.{ModelSnapshot, ModelSnapshotMetaData}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import scala.util.Try

/**
 * Manages the persistence of model snapshots.
 *
 * @constructor Creates a new ModelSnapshotStore using the provided database pool.
 * @param dbProvider The database pool to use for connections.
 */
class ModelSnapshotStore private[domain](dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import ModelSnapshotStore._
  import com.convergencelabs.convergence.server.backend.datastore.domain.schema.DomainSchema._

  val AllFields = s"version, timestamp, model.collection.id as collectionId, model.id as modelId, data"
  val MetaDataFields = s"version, timestamp, model.collection.id as collectionId, model.id as modelId"

  /**
   * Creates a new snapshot in the database.  The combination of modelId and
   * version must be unique in the database.
   *
   * @param modelSnapshot The snapshot to create.
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
   * @param id      The model id of the snapshot to get.
   * @param version The version of the snapshot to get.
   * @return Some(SnapshotData) if a snapshot corresponding to the model and
   *         version if it exists, or None if it does not.
   */
  def getSnapshot(id: String, version: Long): Try[Option[ModelSnapshot]] = withDb { db =>
    val query = s"SELECT $AllFields FROM ModelSnapshot WHERE model.id = :modelId AND version = :version"
    val params = Map(Constants.ModelId -> id, Classes.ModelSnapshot.Fields.Version -> version)
    OrientDBUtil.findDocumentAndMap(db, query, params)(ModelSnapshotStore.docToModelSnapshot)
  }

  /**
   * Gets snapshots for the specified model.
   *
   * @param id The model id of the snapshot to get.
   * @return Some(SnapshotData) if a snapshot corresponding to the model and
   *         version if it exists, or None if it does not.
   */
  def getSnapshots(id: String): Try[List[ModelSnapshot]] = withDb { db =>
    val query = s"SELECT $AllFields FROM ModelSnapshot WHERE model.id = :modelId"
    val params = Map(Constants.ModelId -> id)
    OrientDBUtil.queryAndMap(db, query, params)(docToModelSnapshot)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Meta Data
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Gets a listing of all snapshot meta data for a given model.  The results
   * are sorted in ascending order by version.
   *
   * @param id     The id of the model to get the meta data for.
   * @param offset The offset into the result list.  If None, the default is 0.
   * @param limit  The maximum number of results to return.  If None, then all results are returned.
   * @return A list of (paged) meta data for the specified model.
   */
  def getSnapshotMetaDataForModel(id: String,
                                  offset: QueryOffset,
                                  limit: QueryLimit): Try[List[ModelSnapshotMetaData]] = withDb { db =>
    val baseQuery =
      s"""SELECT $MetaDataFields
         |FROM ModelSnapshot
         |WHERE
         |  model.id = :modelId
         |ORDER BY version ASC""".stripMargin
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    val params = Map(Constants.ModelId -> id)
    OrientDBUtil.queryAndMap(db, query, params)(docToModelSnapshotMetaData)
  }

  /**
   * Gets a listing of all snapshot meta data for a given model within time bounds.  The results
   * are sorted in ascending order by version.
   *
   * @param id        The id of the model to get the meta data for.
   * @param startTime The lower time bound (defaults to the unix epoc)
   * @param endTime   The upper time bound (defaults to now)
   * @param offset    The offset into the result list.  If None, the default is 0.
   * @param limit     The maximum number of results to return.  If None, then all results are returned.
   * @return A list of (paged) meta data for the specified model.
   */
  def getSnapshotMetaDataForModelByTime(id: String,
                                        startTime: Option[Long],
                                        endTime: Option[Long],
                                        offset: QueryOffset,
                                        limit: QueryLimit): Try[List[ModelSnapshotMetaData]] = withDb { db =>
    val baseQuery =
      s"""SELECT $MetaDataFields
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
    OrientDBUtil.queryAndMap(db, query, params)(docToModelSnapshotMetaData)
  }

  /**
   * Returns the most recent snapshot meta data for the specified model.
   *
   * @param id The id of the model to get the latest snapshot meta data for.
   * @return the latest snapshot (by version) of the specified model, or None
   *         if no snapshots for that model exist.
   */
  def getLatestSnapshotMetaDataForModel(id: String): Try[Option[ModelSnapshotMetaData]] = withDb { db =>
    val query =
      s"""SELECT $MetaDataFields
         |FROM ModelSnapshot
         |WHERE
         |  model.id = :modelId
         |ORDER BY version DESC LIMIT 1""".stripMargin
    val params = Map(Constants.ModelId -> id)
    OrientDBUtil.findDocumentAndMap(db, query, params)(docToModelSnapshotMetaData)
  }

  def getClosestSnapshotByVersion(id: String, version: Long): Try[Option[ModelSnapshot]] = withDb { db =>
    val query =
      s"""SELECT
         |  abs($$current.version - $version) as abs_delta,
         |  $$current.version - $version as delta,
         |  $AllFields
         |FROM ModelSnapshot
         |WHERE
         |  model.id = :modelId
         |ORDER BY
         |  abs_delta ASC,
         |  delta DESC
         |LIMIT 1""".stripMargin
    val params = Map(Constants.ModelId -> id, Classes.ModelSnapshot.Fields.Version -> version)
    OrientDBUtil.findDocumentAndMap(db, query, params)(docToModelSnapshot)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Removal
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Removes a snapshot for a model at a specific version.
   *
   * @param id      The id of the model to delete the snapshot for.
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
  def removeAllSnapshotsForModel(id: String, db: Option[ODatabaseDocument] = None): Try[Unit] = withDb(db) { db =>
    val params = Map(Constants.ModelId -> id)
    OrientDBUtil.commandReturningCount(db, DeleteAllSnapshotsForModelCommand, params).map(_ => ())
  }

  val DeleteAllSnapshotsForModelCommand = "DELETE FROM ModelSnapshot WHERE model.id = :modelId"

  /**
   * Removes all snapshots for all models in a collection
   *
   * @param collectionId The id of the collection to delete all snapshots for.
   */
  def removeAllSnapshotsForCollection(collectionId: String): Try[Unit] = withDb { db =>
    ModelSnapshotStore.removeAllSnapshotsForCollection(collectionId, db)
  }
}


object ModelSnapshotStore {

  import com.convergencelabs.convergence.server.backend.datastore.domain.schema.DomainSchema._

  object Constants {
    val CollectionId = "collectionId"
    val ModelId = "modelId"
  }

  def docToModelSnapshot(doc: ODocument): ModelSnapshot = {
    val dataDoc: ODocument = doc.getProperty(Classes.ModelSnapshot.Fields.Data)
    val data = dataDoc.asObjectValue
    model.ModelSnapshot(docToModelSnapshotMetaData(doc), data)
  }

  def modelSnapshotToDoc(modelSnapshot: ModelSnapshot, db: ODatabaseDocument): Try[ODocument] = {
    val md = modelSnapshot.metaData
    ModelStore.getModelRid(md.modelId, db).flatMap { modelRid =>
      Try {
        val doc: ODocument = db.newInstance(Classes.ModelSnapshot.ClassName)
        doc.setProperty(Classes.ModelSnapshot.Fields.Model, modelRid)
        doc.setProperty(Classes.ModelSnapshot.Fields.Version, modelSnapshot.metaData.version)
        doc.setProperty(Classes.ModelSnapshot.Fields.Timestamp, new Date(modelSnapshot.metaData.timestamp.toEpochMilli))
        doc.setProperty(Classes.ModelSnapshot.Fields.Data, modelSnapshot.data.asODocument)
        doc
      }
    }
  }

  def docToModelSnapshotMetaData(doc: ODocument): ModelSnapshotMetaData = {
    val timestamp: java.util.Date = doc.getProperty(Classes.ModelSnapshot.Fields.Timestamp)
    model.ModelSnapshotMetaData(
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
    OrientDBUtil.commandReturningCount(db, command, params).map(_ => ())
  }
}