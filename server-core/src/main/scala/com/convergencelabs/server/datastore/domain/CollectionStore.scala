package com.convergencelabs.server.datastore.domain

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.domain.mapper.CollectionMapper.CollectionToODocument
import com.convergencelabs.server.datastore.domain.mapper.CollectionMapper.ODocumentToCollection
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

object CollectionStore {
  private val CollectionId = "collectionId"
}

class CollectionStore private[domain] (dbPool: OPartitionedDatabasePool, modelStore: ModelStore)
    extends AbstractDatabasePersistence(dbPool) {

  def collectionExists(collectionId: String): Try[Boolean] = tryWithDb { db =>
    val queryString =
      """SELECT collectionId
        |FROM Collection
        |WHERE
        |  collectionId = :collectionId""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(CollectionStore.CollectionId -> collectionId)
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
    try {
      db.save(collection.asODocument)
      CreateSuccess(())
    } catch {
      case e: ORecordDuplicatedException => DuplicateValue
    }
  }

  def updateCollection(collection: Collection): Try[UpdateResult] = tryWithDb { db =>
    val updatedDoc = collection.asODocument

    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Collection WHERE collectionId = :collectionId")
    val params = Map(CollectionStore.CollectionId -> collection.id)
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

  def deleteCollection(collectionId: String): Try[DeleteResult] = tryWithDb { db =>
    modelStore.deleteAllModelsInCollection(collectionId)

    val queryString =
      """DELETE FROM Collection
        |WHERE
        |  collectionId = :collectionId""".stripMargin

    val command = new OCommandSQL(queryString)
    val params = Map(CollectionStore.CollectionId -> collectionId)
    val deleted: Int = db.command(command).execute(params.asJava)
    deleted match {
      case 0 => NotFound
      case _ => DeleteSuccess
    }
  }

  def getCollection(collectionId: String): Try[Option[Collection]] = tryWithDb { db =>
    val queryString =
      """SELECT *
        |FROM Collection
        |WHERE
        |  collectionId = :collectionId""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(CollectionStore.CollectionId -> collectionId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result) { _.asCollection }
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
    result.asScala.toList map { _.asCollection }
  }
}
