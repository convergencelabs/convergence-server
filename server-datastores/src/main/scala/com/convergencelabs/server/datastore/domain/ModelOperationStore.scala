package com.convergencelabs.server.datastore.domain

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelOperation
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import scala.util.Try
import java.time.Instant
import com.convergencelabs.server.datastore.QueryUtil
import scala.collection.JavaConverters._
import com.convergencelabs.server.datastore.domain.mapper.ModelOperationMapper._

class ModelOperationStore private[domain] (dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool) {

  val CollectionId = "collectionId"
  val ModelId = "modelId"

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def getMaxVersion(fqn: ModelFqn): Try[Option[Long]] = tryWithDb { db =>
    val db = dbPool.acquire()
    val queryString =
      """SELECT max(version)
        |FROM ModelOperation
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)

    db.close()

    result.asScala.toList match {
      case doc :: Nil => Some(doc.field("max", OType.LONG))
      case _          => None
    }
  }

  def getVersionAtOrBeforeTime(fqn: ModelFqn, time: Instant): Try[Option[Long]] = tryWithDb { db =>
    val db = dbPool.acquire()
    val queryString =
      """SELECT max(version)
        |FROM ModelOperation
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId AND
        |  timestamp <= :time""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, "time" -> new java.util.Date(time.toEpochMilli()))
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList match {
      case doc :: rest => Some(doc.field("max", OType.LONG))
      case Nil         => None
    }
  }

  def getOperationsAfterVersion(fqn: ModelFqn, version: Long): Try[List[ModelOperation]] = tryWithDb { db =>
    val db = dbPool.acquire()
    val queryString =
      """SELECT * FROM ModelOperation
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId AND
        |  version >= :version
        |ORDER BY version ASC""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId, "version" -> version)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList map { _.asModelOperation }
  }

  def getOperationsAfterVersion(fqn: ModelFqn, version: Long, limit: Int): Try[List[ModelOperation]] = tryWithDb { db =>
    val db = dbPool.acquire()
    val queryString =
      """SELECT * FROM ModelOperation
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId AND
        | version >= :version
        |ORDER BY version ASC""".stripMargin

    val query = new OSQLSynchQuery[ODocument](QueryUtil.buildPagedQuery(queryString, Some(limit), None))
    val params = Map(
      CollectionId -> fqn.collectionId,
      ModelId -> fqn.modelId,
      "version" -> version,
      "limit" -> limit)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList map { _.asModelOperation }
  }

  def deleteAllOperationsForModel(fqn: ModelFqn): Try[Unit] = tryWithDb { db =>
    val db = dbPool.acquire()
    val commandString =
      """DELETE FROM ModelOperation
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId""".stripMargin

    val params = Map(CollectionId -> fqn.collectionId, ModelId -> fqn.modelId)
    val command = new OCommandSQL(commandString)
    db.command(command).execute(params.asJava)
    db.close()
  }

  def deleteAllOperationsForCollection(collectionId: String): Try[Unit] = tryWithDb { db =>
    val db = dbPool.acquire()
    val commandString =
      """DELETE FROM ModelOperation
        |WHERE
        |  collectionId = :collectionId""".stripMargin

    val params = Map(CollectionId -> collectionId)
    val command = new OCommandSQL(commandString)
    db.command(command).execute(params.asJava)
    db.close()
  }
}

