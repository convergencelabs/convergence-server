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
import com.convergencelabs.server.datastore.DuplicateValueExcpetion
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ModelSnapshotConfigToODocument
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ODocumentToModelSnapshotConfig
import com.convergencelabs.server.domain.model.Collection
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.convergencelabs.server.domain.ModelSnapshotConfig
import java.time.Duration
import com.convergencelabs.server.datastore.domain.CollectionStore.CollectionSummary

object CollectionStore {
  val ClassName = "Collection"
  val CollectionIdIndex = "Collection.id"

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
    val doc = new ODocument(ClassName)
    setCollectionFieldsInDoc(collection, doc)
    doc
  }
  
  def setCollectionFieldsInDoc(collection: Collection, doc: ODocument): Unit = {
    doc.field(Id, collection.id)
    doc.field(Name, collection.name)
    doc.field(OverrideSnapshotConfig, collection.overrideSnapshotConfig)
    doc.field(SnapshotConfig, collection.snapshotConfig.asODocument, OType.EMBEDDED)
    doc.field(WorldPermissions, ModelPermissionsStore.collectionPermissionToDoc(collection.worldPermissions))
  }

  def docToCollection(doc: ODocument): Collection = {
    val snapshotConfigDoc: ODocument = doc.field(SnapshotConfig, OType.EMBEDDED);
    val snapshotConfig = Option(snapshotConfigDoc) map (_.asModelSnapshotConfig) getOrElse (CollectionStore.DefaultSnapshotConfig)
    Collection(
      doc.field(Id),
      doc.field(Name),
      doc.field(OverrideSnapshotConfig),
      snapshotConfig,
      ModelPermissionsStore.docToCollectionPermissions(doc.field(WorldPermissions)))
  }

  def getCollectionRid(id: String, db: ODatabaseDocumentTx): Try[ORID] = {
    QueryUtil.getRidFromIndex(CollectionIdIndex, id, db)
  }
  
  case class CollectionSummary(id: String, description: String, modelCount: Int)
}

class CollectionStore private[domain] (dbProvider: DatabaseProvider, modelStore: ModelStore)
    extends AbstractDatabasePersistence(dbProvider) {

  def collectionExists(id: String): Try[Boolean] = tryWithDb { db =>
    val query = "SELECT id FROM Collection WHERE id = :id"
    val params = Map(CollectionStore.Id -> id)
    val results = QueryUtil.query(query, params, db)
    !results.isEmpty
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
  } recoverWith {
    case e: ORecordDuplicatedException => handleDuplicateValue(e)
  }

  def updateCollection(collectionId: String, collection: Collection): Try[Unit] = tryWithDb { db =>
    val params = Map(CollectionStore.Id -> collectionId)
    QueryUtil.getFromIndex(CollectionStore.CollectionIdIndex, collectionId, db) match {
      case Some(existingDoc) =>
        CollectionStore.setCollectionFieldsInDoc(collection, existingDoc)
        existingDoc.save()
        ()
      case None =>
        throw new EntityNotFoundException()
    }
  } recoverWith {
    case e: ORecordDuplicatedException => handleDuplicateValue(e)
  }

  def deleteCollection(id: String): Try[Unit] = tryWithDb { db =>
    modelStore.deleteAllModelsInCollection(id)

    val queryString =
      """DELETE FROM Collection
        |WHERE
        |  id = :id""".stripMargin

    val command = new OCommandSQL(queryString)
    val params = Map(CollectionStore.Id -> id)
    val deleted: Int = db.command(command).execute(params.asJava)
    deleted match {
      case 0 => throw EntityNotFoundException()
      case _ => ()
    }
  }

  def getCollection(id: String): Try[Option[Collection]] = tryWithDb { db =>
    val query = "SELECT FROM Collection WHERE id = :id"
    val params = Map(CollectionStore.Id -> id)
    QueryUtil.lookupOptionalDocument(query, params, db) map { CollectionStore.docToCollection(_) }
  }

  def getOrCreateCollection(collectionId: String): Try[Collection] = {
    this.ensureCollectionExists(collectionId)
    this.getCollection(collectionId).map { x => x.get }
  }

  def getAllCollections(
    offset: Option[Int],
    limit: Option[Int]): Try[List[Collection]] = tryWithDb { db =>

    val queryString = "SELECT * FROM Collection ORDER BY id ASC"
    val pageQuery = QueryUtil.buildPagedQuery(queryString, limit, offset)
    val query = new OSQLSynchQuery[ODocument](pageQuery)
    val result: JavaList[ODocument] = db.command(query).execute()
    result.asScala.toList map { CollectionStore.docToCollection(_) }
  }
  
  def getCollectionSummaries(
    offset: Option[Int],
    limit: Option[Int]): Try[List[CollectionSummary]] = tryWithDb { db =>

    val queryString = "SELECT id, name FROM Collection ORDER BY id ASC"
    val pageQuery = QueryUtil.buildPagedQuery(queryString, limit, offset)
    val query = new OSQLSynchQuery[ODocument](pageQuery)
    val result: JavaList[ODocument] = db.command(query).execute()
    
    val modelCountQuery = "SELECT count(id) as count, collection.id as collectionId FROM Model GROUP BY (collection)"
    val query2 = new OSQLSynchQuery[ODocument](modelCountQuery)
    val result2: JavaList[ODocument] = db.command(query2).execute()
    
    val modelCounts = result2.asScala.toList.map (t => (t.field("collectionId").asInstanceOf[String] -> t.field("count"))) toMap
    
    result.asScala.toList.map(doc => {
      val id: String = doc.field("id")
      val count: Long = modelCounts.get(id).getOrElse(0)
      CollectionSummary(
          id,
          doc.field("name"),
          count.toInt
      )
    })
  }

  private[this] def handleDuplicateValue[T](e: ORecordDuplicatedException): Try[T] = {
    e.getIndexName match {
      case CollectionStore.CollectionIdIndex =>
        Failure(DuplicateValueExcpetion(CollectionStore.Id))
      case _ =>
        Failure(e)
    }
  }
}
