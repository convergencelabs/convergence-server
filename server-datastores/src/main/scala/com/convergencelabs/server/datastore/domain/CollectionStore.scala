package com.convergencelabs.server.datastore.domain

import java.util.{ List => JavaList }
import scala.util.Try
import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.domain.model.Collection
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.datastore.CreateResult
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.UpdateSuccess
import com.convergencelabs.server.datastore.InvalidValue
import java.util.{ List => JavaList }
import scala.collection.JavaConverters._
import com.orientechnologies.orient.core.metadata.schema.OType
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ModelSnapshotConfigToODocument
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ODocumentToModelSnapshotConfig

import CollectionStore._
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.record.OIdentifiable

object CollectionStore {
  val DocumentClassName = "Collection"

  val Id = "id"
  val Name = "name"
  val OverrideSnapshotConfig = "overrideSnapshotConfig"
  val SnapshotConfig = "snapshotConfig"

  def collectionToDoc(collection: Collection): ODocument = {
    val doc = new ODocument(DocumentClassName)
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
    val query = "SELECT @RID as rid FROM Collection WHERE id = :id"
    val params = Map("id" -> id)
    QueryUtil.lookupMandatoryDocument(query, params, db) map { doc =>
      val ridDoc: ODocument = doc.field("rid")
      val rid = ridDoc.getIdentity
      rid
    }
  }
}

class CollectionStore private[domain] (dbPool: OPartitionedDatabasePool, modelStore: ModelStore)
    extends AbstractDatabasePersistence(dbPool) {

  def collectionExists(id: String): Try[Boolean] = tryWithDb { db =>
    val queryString =
      """SELECT id
        |FROM Collection
        |WHERE
        |  id = :id""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(CollectionStore.Id -> id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    !result.isEmpty()
  }

  def ensureCollectionExists(collectionId: String): Try[Unit] = tryWithDb { db =>
    this.collectionExists(collectionId).map {
      case true =>
        ()
      case false =>
        createCollection(Collection(collectionId, collectionId, false, None))
    }
  }

  def createCollection(collection: Collection): Try[CreateResult[Unit]] = tryWithDb { db =>
    val doc = collectionToDoc(collection)
    db.save(doc)
    CreateSuccess(())
  } recover {
    case e: ORecordDuplicatedException => DuplicateValue
  }

  def updateCollection(collection: Collection): Try[UpdateResult] = tryWithDb { db =>
    val updatedDoc = collectionToDoc(collection)

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
    val queryString =
      """SELECT *
        |FROM Collection
        |WHERE
        |  id = :id""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(CollectionStore.Id -> id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result) { docToCollection(_) }
  }

  def getOrCreateCollection(collectionId: String): Try[Collection] = {
    this.ensureCollectionExists(collectionId)
    this.getCollection(collectionId).map { x => x.get }
  }

  def getAllCollections(
    offset: Option[Int],
    limit: Option[Int]): Try[List[Collection]] = tryWithDb { db =>

    val queryString =
      """SELECT *
        |FROM Collection
        |ORDER BY
        |  collectionId ASC""".stripMargin

    val pageQuery = QueryUtil.buildPagedQuery(
      queryString,
      limit,
      offset)

    val query = new OSQLSynchQuery[ODocument](pageQuery)
    val result: JavaList[ODocument] = db.command(query).execute()
    result.asScala.toList map { docToCollection(_) }
  }
}
