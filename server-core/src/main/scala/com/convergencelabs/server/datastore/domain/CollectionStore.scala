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

object CollectionStore {
  private val CollectionId = "collectionId"
}

class CollectionStore private[domain] (dbPool: OPartitionedDatabasePool)
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
        createCollection(Collection(collectionId, "", false, None))
    }
  }

  def createCollection(collection: Collection): Try[Unit] = tryWithDb { db =>
    db.save(collection.asODocument)
    Unit
  }

  def updateCollection(collection: Collection): Try[Unit] = tryWithDb { db =>
    val updatedDoc = collection.asODocument

    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Collection WHERE collectionId = :collectionId")
    val params = Map(CollectionStore.CollectionId -> collection.id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        doc.merge(updatedDoc, false, false)
        db.save(doc)
        ()
      case None =>
        throw new IllegalArgumentException("Collection not found")
    }
  }

  def deleteCollection(collectionId: String): Try[Unit] = tryWithDb { db =>
    val queryString =
      """DELETE FROM Collection
        |WHERE
        |  collectionId = :collectionId""".stripMargin

    val command = new OCommandSQL(queryString)
    val params = Map(CollectionStore.CollectionId -> collectionId)
    val deleted: Int = db.command(command).execute(params.asJava)
    deleted match {
      case 1 => Unit
      case _ => throw new IllegalArgumentException("The model could not be deleted because it did not exist")
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

  def getOrCreateCollection(collectionId: String): Try[Option[Collection]] = {
    this.ensureCollectionExists(collectionId)
    this.getCollection(collectionId)
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
