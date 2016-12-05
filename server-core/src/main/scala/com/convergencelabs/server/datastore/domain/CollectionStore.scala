package com.convergencelabs.server.datastore.domain

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.UpdateSuccess
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

object CollectionStore {
  val ClassName = "Collection"
  val CollectionIdIndex = "Collection.id"

  val Id = "id"
  val Name = "name"
  val OverrideSnapshotConfig = "overrideSnapshotConfig"
  val SnapshotConfig = "snapshotConfig"

  def collectionToDoc(collection: Collection): ODocument = {
    val doc = new ODocument(ClassName)
    doc.field(Id, collection.id)
    doc.field(Name, collection.name)
    doc.field(OverrideSnapshotConfig, collection.overrideSnapshotConfig)
    collection.snapshotConfig.foreach { config =>
      doc.field(SnapshotConfig, config.asODocument)
    }
    doc
  }

  def docToCollection(doc: ODocument): Collection = {
    val snapshotConfig: ODocument = doc.field(SnapshotConfig, OType.EMBEDDED);
    Collection(
      doc.field(Id),
      doc.field(Name),
      doc.field(OverrideSnapshotConfig),
      Option(snapshotConfig).map { _.asModelSnapshotConfig })
  }

  def getCollectionRid(id: String, db: ODatabaseDocumentTx): Try[ORID] = {
    QueryUtil.getRidFromIndex(CollectionIdIndex, id, db)
  }
}

class CollectionStore private[domain] (dbProvider: DatabaseProvider, modelStore: ModelStore)
    extends AbstractDatabasePersistence(dbProvider) {

  def collectionExists(id: String): Try[Boolean] = tryWithDb { db =>
    val query = "SELECT id FROM Collection WHERE id = :id"
    val params = Map(CollectionStore.Id -> id)
    val results = QueryUtil.query(query, params, db)
    !results.isEmpty
  }

  def ensureCollectionExists(collectionId: String): Try[Unit] = {
    this.collectionExists(collectionId).flatMap {
      case true =>
        Success(())
      case false =>
        createCollection(Collection(collectionId, collectionId, false, None)).map { _ => () }
    }
  }

  def createCollection(collection: Collection): Try[CreateResult[Unit]] = tryWithDb { db =>
    val doc = CollectionStore.collectionToDoc(collection)
    db.save(doc)
    CreateSuccess(())
  } recover {
    case e: ORecordDuplicatedException => DuplicateValue
  }

  def updateCollection(collection: Collection): Try[UpdateResult] = tryWithDb { db =>
    val updatedDoc = CollectionStore.collectionToDoc(collection)

    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Collection WHERE id = :id")
    val params = Map(CollectionStore.Id -> collection.id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        try {
          doc.merge(updatedDoc, false, false)
          db.save(doc)
          UpdateSuccess
        } catch {
          case e: ORecordDuplicatedException => InvalidValue
        }

      case None => NotFound
    }
  }

  def deleteCollection(id: String): Try[DeleteResult] = tryWithDb { db =>
    modelStore.deleteAllModelsInCollection(id)

    val queryString =
      """DELETE FROM Collection
        |WHERE
        |  id = :id""".stripMargin

    val command = new OCommandSQL(queryString)
    val params = Map(CollectionStore.Id -> id)
    val deleted: Int = db.command(command).execute(params.asJava)
    deleted match {
      case 0 => NotFound
      case _ => DeleteSuccess
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

    val queryString = "SELECT * FROM Collection ORDER BY collectionId ASC"
    val pageQuery = QueryUtil.buildPagedQuery(queryString, limit, offset)
    val query = new OSQLSynchQuery[ODocument](pageQuery)
    val result: JavaList[ODocument] = db.command(query).execute()
    result.asScala.toList map { CollectionStore.docToCollection(_) }
  }
}
