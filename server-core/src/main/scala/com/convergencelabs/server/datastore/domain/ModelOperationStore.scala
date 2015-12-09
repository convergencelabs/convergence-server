package com.convergencelabs.server.datastore.domain

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import com.convergencelabs.server.datastore.domain.mapper.ModelOperationMapper.ODocumentToModelOperation
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelOperation
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import scala.util.Try

class ModelOperationStore private[domain] (dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool) {
  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def getMaxVersion(fqn: ModelFqn): Try[Option[Long]] = tryWithDb { db =>
    val db = dbPool.acquire()
    val queryString =
      """SELECT max(operation.version)
        |FROM ModelOperation
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)

    db.close()

    result.asScala.toList match {
      case doc :: Nil => Some(doc.field("max", OType.LONG))
      case _ => None
    }
  }

  def getVersionAtOrBeforeTime(fqn: ModelFqn, time: Long): Try[Option[Long]] = tryWithDb { db =>
    val db = dbPool.acquire()
    val queryString =
      """SELECT max(operation.version)
        |FROM ModelOperation
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId AND
        |  operation.time <= :time""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "time" -> time)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList match {
      case doc :: rest => Some(doc.field("max", OType.LONG))
      case Nil => None
    }
  }

  def getOperationsAfterVersion(fqn: ModelFqn, version: Long): Try[List[ModelOperation]] = tryWithDb { db =>
    val db = dbPool.acquire()
    val queryString =
      """SELECT * FROM ModelOperation
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId AND
        |  operation.version >= :version
        |ORDER BY operation.version ASC""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId, "version" -> version)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList map { _.asModelOperation }
  }

  def getOperationsAfterVersion(fqn: ModelFqn, version: Long, limit: Int): Try[List[ModelOperation]] = tryWithDb { db =>
    val db = dbPool.acquire()
    val queryStirng =
      """SELECT * FROM ModelOperation
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId AND
        | operation.version >= :version
        |ORDER BY operation.version ASC LIMIT :limit""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryStirng)
    val params = Map(
      "collectionId" -> fqn.collectionId,
      "modelId" -> fqn.modelId,
      "version" -> version,
      "limit" -> limit)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList map { _.asModelOperation }
  }

  def removeOperationsForModel(fqn: ModelFqn): Try[Unit] = tryWithDb { db =>
    val db = dbPool.acquire()
    val queryStirng =
      """DELETE FROM ModelOperation
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryStirng)
    val params = Map(
      "collectionId" -> fqn.collectionId,
      "modelId" -> fqn.modelId)
    val command = new OCommandSQL("DELETE FROM User WHERE uid = :uid")
    db.command(command).execute(params.asJava)
    db.close()
  }
}

