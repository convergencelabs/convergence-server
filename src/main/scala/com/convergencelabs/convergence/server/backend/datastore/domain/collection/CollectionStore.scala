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

package com.convergencelabs.convergence.server.backend.datastore.domain.collection

import java.time.Duration
import java.util

import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ModelSnapshotConfigMapper.{ModelSnapshotConfigToODocument, ODocumentToModelSnapshotConfig}
import com.convergencelabs.convergence.server.backend.datastore.domain.schema.CollectionClass.{ClassName, Fields, Indices}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.{CollectionPermissions, ModelPermissionsStore, ModelStore}
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, DuplicateValueException, OrientDBUtil}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.domain
import com.convergencelabs.convergence.server.model.domain.ModelSnapshotConfig
import com.convergencelabs.convergence.server.model.domain.collection.{Collection, CollectionSummary}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object CollectionStore {

  private val DefaultSnapshotConfig = domain.ModelSnapshotConfig(
    snapshotsEnabled = false,
    triggerByVersion = false,
    limitedByVersion = false,
    1000,
    1000,
    triggerByTime = false,
    limitedByTime = false,
    Duration.ofMillis(600000),
    Duration.ofMillis(600000))

  private val DefaultWorldPermissions = CollectionPermissions(create = true, read = true, write = true, remove = true, manage = true)

  def collectionToDoc(collection: Collection): ODocument = {
    val doc = new ODocument(ClassName)
    setCollectionFieldsInDoc(collection, doc)
    doc
  }

  def setCollectionFieldsInDoc(collection: Collection, doc: ODocument): Unit = {
    doc.setProperty(Fields.Id, collection.id)
    doc.setProperty(Fields.Description, collection.description)
    doc.setProperty(Fields.OverrideSnapshotConfig, collection.overrideSnapshotConfig)
    doc.setProperty(Fields.SnapshotConfig, collection.snapshotConfig.asODocument, OType.EMBEDDED)
    doc.setProperty(Fields.WorldPermissions, ModelPermissionsStore.collectionPermissionToDoc(collection.worldPermissions))
  }

  def docToCollection(doc: ODocument): Collection = {
    val snapshotConfigDoc: ODocument = doc.getProperty(Fields.SnapshotConfig)
    val snapshotConfig = Option(snapshotConfigDoc) map (_.asModelSnapshotConfig) getOrElse CollectionStore.DefaultSnapshotConfig
    domain.collection.Collection(
      doc.getProperty(Fields.Id),
      doc.getProperty(Fields.Description),
      doc.getProperty(Fields.OverrideSnapshotConfig),
      snapshotConfig,
      ModelPermissionsStore.docToCollectionPermissions(doc.getProperty(Fields.WorldPermissions)))
  }

  def getCollectionRid(id: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Indices.Id, id)
  }
}

class CollectionStore private[domain](dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider) {


  def collectionExists(id: String): Try[Boolean] = withDb { db =>
    val query = "SELECT id FROM Collection WHERE id = :id"
    val params = Map(Fields.Id -> id)
    OrientDBUtil.query(db, query, params).map(_.nonEmpty)
  }

  //TODO: Do we need to be passing permissions in here
  def ensureCollectionExists(collectionId: String): Try[Unit] = {
    this.collectionExists(collectionId).flatMap {
      case true =>
        Success(())
      case false =>
        createCollection(domain.collection.Collection(collectionId, collectionId, overrideSnapshotConfig = false, CollectionStore.DefaultSnapshotConfig, CollectionStore.DefaultWorldPermissions)).map { _ => () }
    }
  }

  def createCollection(collection: Collection): Try[Unit] = tryWithDb { db =>
    val doc = CollectionStore.collectionToDoc(collection)
    db.save(doc)
    ()
  } recoverWith handleDuplicateValue

  def updateCollection(collectionId: String, collection: Collection): Try[Unit] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Indices.Id, collectionId)
      .map { existingDoc =>
        CollectionStore.setCollectionFieldsInDoc(collection, existingDoc)
        existingDoc.save()
        ()
      }
  } recoverWith handleDuplicateValue

  def deleteCollection(id: String): Try[Unit] = withDb { db =>
    for {
      _ <- ModelStore.deleteAllModelsInCollection(id, db)
      _ <- {
        val query = "DELETE FROM Collection WHERE id = :id"
        val params = Map(Fields.Id -> id)
        OrientDBUtil.mutateOneDocument(db, query, params)
      }
    } yield ()
  }

  def getCollection(id: String): Try[Option[Collection]] = withDb { db =>
    val query = "SELECT FROM Collection WHERE id = :id"
    val params = Map(Fields.Id -> id)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(CollectionStore.docToCollection))
  }

  def getOrCreateCollection(collectionId: String): Try[Collection] = {
    ensureCollectionExists(collectionId)
      .flatMap(_ => getCollection(collectionId).map(_.get))
  }

  def getAllCollections(idFilter: Option[String],
                        offset: QueryOffset,
                        limit: QueryLimit): Try[PagedData[Collection]] = withDb { db =>
    val (whereClause, whereParams) = idFilter match {
      case Some(filter) =>
        val w = " WHERE id.toLowerCase() LIKE :filter"
        val p = Map("filter" -> s"%${filter.toLowerCase}%")
        (w, p)
      case None =>
        ("", Map[String, Any]())
    }
    val queryString = s"SELECT * FROM Collection$whereClause ORDER BY id ASC"
    val query = OrientDBUtil.buildPagedQuery(queryString, limit, offset)
    val countQuery = s"SELECT count(*) as count FROM Collection$whereClause"
    for {
      count <- OrientDBUtil.getDocument(db, countQuery, whereParams).map(_.getProperty("count").asInstanceOf[Long])
      collections <- OrientDBUtil
        .query(db, query, whereParams)
        .map(_.map(CollectionStore.docToCollection))
    } yield {
      PagedData[Collection](collections, offset.getOrZero, count)
    }
  }

  def getCollectionSummaries(filter: Option[String],
                             offset: QueryOffset,
                             limit: QueryLimit): Try[PagedData[CollectionSummary]] = withDb { db =>
    val (whereClause, whereParams) = filter match {
      case Some(filter) =>
        val w = " WHERE id.toLowerCase() LIKE :filter OR description.toLowerCase() LIKE :filter"
        val p = Map("filter" -> s"%${filter.toLowerCase}%")
        (w, p)
      case None =>
        ("", Map[String, Any]())
    }
    val baseQuery = s"SELECT @rid, id, description FROM Collection$whereClause ORDER BY id ASC"
    val countQuery = s"SELECT count(*) as count FROM Collection$whereClause ORDER BY id ASC"
    val collectionsQuery = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    for {
      allCollections <- OrientDBUtil.query(db, collectionsQuery, whereParams)
      collectionCount <- OrientDBUtil.getDocument(db, countQuery, whereParams).map(_.getProperty("count").asInstanceOf[Long])
      modelsPerCollection <- {
        val collectionRids = allCollections.map(_.getProperty("@rid").asInstanceOf[ORID])
        val modelCountQuery = "SELECT count(*) as count, collection.id as collectionId FROM Model WHERE collection IN :collections GROUP BY (collection)"
        val params = Map("collections" -> new util.ArrayList(collectionRids.asJava))
        OrientDBUtil.query(db, modelCountQuery, params)
      }
    } yield {
      val modelCounts = modelsPerCollection.map(t => t.getProperty("collectionId").asInstanceOf[String] -> t.getProperty("count")).toMap
      val summaries = allCollections map { doc =>
        val id: String = doc.getProperty(Fields.Id)
        val description: String = doc.getProperty(Fields.Description)
        val count: Long = modelCounts.getOrElse(id, 0)
        CollectionSummary(id, description, count)
      }

      PagedData[CollectionSummary](summaries, offset.getOrZero, collectionCount)
    }
  }

  private[this] def handleDuplicateValue[T]: PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Indices.Id =>
          Failure(DuplicateValueException(Fields.Id))
        case _ =>
          Failure(e)
      }
  }
}
