package com.convergencelabs.server.datastore.domain

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.language.postfixOps

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ModelSnapshotConfigToODocument
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ODocumentToModelSnapshotConfig
import com.convergencelabs.server.domain.model.Collection
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.convergencelabs.server.domain.ModelSnapshotConfig
import java.time.Duration
import com.convergencelabs.server.datastore.domain.CollectionStore.CollectionSummary
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.convergencelabs.server.datastore.OrientDBUtil

object CollectionStore {
  val Id = "id"
  val Name = "name"
  val OverrideSnapshotConfig = "overrideSnapshotConfig"
  val SnapshotConfig = "snapshotConfig"
  val WorldPermissions = "worldPermissions"

  val DefaultSnapshotConfig = ModelSnapshotConfig(
    false,
    false,
    false,
    1000,
    1000,
    false,
    false,
    Duration.ofMillis(600000),
    Duration.ofMillis(600000))

  val DefaultWorldPermissions = CollectionPermissions(true, true, true, true, true)

  def collectionToDoc(collection: Collection): ODocument = {
    val doc = new ODocument(Schema.Classes.Collection.Class)
    setCollectionFieldsInDoc(collection, doc)
    doc
  }

  def setCollectionFieldsInDoc(collection: Collection, doc: ODocument): Unit = {
    doc.setProperty(Id, collection.id)
    doc.setProperty(Name, collection.name)
    doc.setProperty(OverrideSnapshotConfig, collection.overrideSnapshotConfig)
    doc.setProperty(SnapshotConfig, collection.snapshotConfig.asODocument, OType.EMBEDDED)
    doc.setProperty(WorldPermissions, ModelPermissionsStore.collectionPermissionToDoc(collection.worldPermissions))
  }

  def docToCollection(doc: ODocument): Collection = {
    val snapshotConfigDoc: ODocument = doc.field(SnapshotConfig, OType.EMBEDDED);
    val snapshotConfig = Option(snapshotConfigDoc) map (_.asModelSnapshotConfig) getOrElse (CollectionStore.DefaultSnapshotConfig)
    Collection(
      doc.getProperty(Id),
      doc.getProperty(Name),
      doc.getProperty(OverrideSnapshotConfig),
      snapshotConfig,
      ModelPermissionsStore.docToCollectionPermissions(doc.getProperty(WorldPermissions)))
  }

  def getCollectionRid(id: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Schema.Classes.Collection.Indices.Id, id)
  }

  case class CollectionSummary(id: String, description: String, modelCount: Int)
}

class CollectionStore private[domain] (dbProvider: DatabaseProvider, modelStore: ModelStore)
  extends AbstractDatabasePersistence(dbProvider) {

  def collectionExists(id: String): Try[Boolean] = withDb { db =>
    val query = "SELECT id FROM Collection WHERE id = :id"
    val params = Map(CollectionStore.Id -> id)
    OrientDBUtil.query(db, query, params).map(_.isEmpty)
  }

  //TODO: Do we need to be passing permissions in here
  def ensureCollectionExists(collectionId: String): Try[Unit] = {
    this.collectionExists(collectionId).flatMap {
      case true =>
        Success(())
      case false =>
        createCollection(Collection(collectionId, collectionId, false, CollectionStore.DefaultSnapshotConfig, CollectionStore.DefaultWorldPermissions)).map { _ => () }
    }
  }

  def createCollection(collection: Collection): Try[Unit] = tryWithDb { db =>
    val doc = CollectionStore.collectionToDoc(collection)
    db.save(doc)
    ()
  } recoverWith (handleDuplicateValue)

  def updateCollection(collectionId: String, collection: Collection): Try[Unit] = withDb { db =>
    val params = Map(CollectionStore.Id -> collectionId)
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Schema.Classes.Collection.Indices.Id, collectionId)
      .map { existingDoc =>
        CollectionStore.setCollectionFieldsInDoc(collection, existingDoc)
        existingDoc.save()
        ()
      }
  } recoverWith (handleDuplicateValue)

  def deleteCollection(id: String): Try[Unit] = withDb { db =>
    modelStore
      .deleteAllModelsInCollection(id)
      .flatMap { _ =>
        val query = "DELETE FROM Collection WHERE id = :id"
        val params = Map(CollectionStore.Id -> id)
        OrientDBUtil.mutateOneDocument(db, query, params)
      }
  }

  def getCollection(id: String): Try[Option[Collection]] = withDb { db =>
    val query = "SELECT FROM Collection WHERE id = :id"
    val params = Map(CollectionStore.Id -> id)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(CollectionStore.docToCollection(_)))
  }

  def getOrCreateCollection(collectionId: String): Try[Collection] = {
    ensureCollectionExists(collectionId)
      .flatMap(_ => getCollection(collectionId).map(_.get))
  }

  def getAllCollections(
    offset: Option[Int],
    limit: Option[Int]): Try[List[Collection]] = withDb { db =>
    val queryString = "SELECT * FROM Collection ORDER BY id ASC"
    val query = OrientDBUtil.buildPagedQuery(queryString, limit, offset)
    OrientDBUtil
      .query(db, query, Map())
      .map(_.map(CollectionStore.docToCollection(_)))
  }

  def getCollectionSummaries(
    offset: Option[Int],
    limit: Option[Int]): Try[List[CollectionSummary]] = withDb { db =>

    val queryString = "SELECT id, name FROM Collection ORDER BY id ASC"
    val collectionsQuery = OrientDBUtil.buildPagedQuery(queryString, limit, offset)
    OrientDBUtil.query(db, collectionsQuery, Map()).flatMap { allCollections =>
      // FIXME this seems to get all collections. We need to mat
      val modelCountQuery = "SELECT count(id) as count, collection.id as collectionId FROM Model GROUP BY (collection)"
      OrientDBUtil.query(db, modelCountQuery, Map()).map { modelsPerCollection =>
        val modelCounts = modelsPerCollection.map(t => (t.field("collectionId").asInstanceOf[String] -> t.field("count"))) toMap

        allCollections.map(doc => {
          val id: String = doc.field("id")
          val count: Long = modelCounts.get(id).getOrElse(0)
          CollectionSummary(
            id,
            doc.field("name"),
            count.toInt)
        })
      }
    }
  }

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Schema.Classes.Collection.Indices.Id =>
          Failure(DuplicateValueException(CollectionStore.Id))
        case _ =>
          Failure(e)
      }
  }
}
