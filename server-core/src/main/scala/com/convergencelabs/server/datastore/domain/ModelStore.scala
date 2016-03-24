package com.convergencelabs.server.datastore.domain

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try
import org.json4s.JArray
import org.json4s.JBool
import org.json4s.JObject
import org.json4s.JString
import org.json4s.JValue
import org.json4s.JsonAST.JNumber
import org.json4s.NoTypeHints
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization
import org.json4s.jvalue2monadic
import org.json4s.string2JsonInput
import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.domain.mapper.ModelMapper.ODocumentToModel
import com.convergencelabs.server.datastore.domain.mapper.ModelMapper.ODocumentToModelMetaData
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelMetaData
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.id.ORID
import java.util.Date

object ModelStore {
  private val ModelClass = "Model"
  private val Data = "data"
  private val CollectionId = "collectionId"
  private val ModelId = "modelId"
  private val Version = "version"
  private val CreatedTime = "createdTime"
  private val ModifiedTime = "modifiedTime"
}

class ModelStore private[domain] (dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool) {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def modelExists(fqn: ModelFqn): Try[Boolean] = tryWithDb { db =>
    val queryString =
      """SELECT modelId
        |FROM Model
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId""".stripMargin

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(
      ModelStore.CollectionId -> fqn.collectionId,
      ModelStore.ModelId -> fqn.modelId)

    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    !result.isEmpty()
  }

  def createModel(model: Model): Try[Unit] = tryWithDb { db =>
    db.begin()
    val modelDoc = new ODocument(ModelStore.ModelClass)

    val ModelMetaData(ModelFqn(collectionId, modelId), version, createdTime, modifiedTime) = model.metaData
    modelDoc.field(ModelStore.CollectionId, collectionId)
    modelDoc.field(ModelStore.ModelId, modelId)
    modelDoc.field(ModelStore.Version, version)
    modelDoc.field(ModelStore.CreatedTime, Date.from(createdTime))
    modelDoc.field(ModelStore.ModifiedTime, Date.from(modifiedTime))
    modelDoc.save()
    modelDoc.field(ModelStore.Data, OrientDataValueBuilder.objectValueToODocument(model.data, modelDoc))
    modelDoc.save()
    db.commit()
    Unit
  }

  def deleteModel(fqn: ModelFqn): Try[Unit] = tryWithDb { db =>
    val queryString =
      "DELETE FROM Model WHERE collectionId = :collectionId AND modelId = :modelId"
    val command = new OCommandSQL(queryString)
    val params = Map(
      ModelStore.CollectionId -> fqn.collectionId,
      ModelStore.ModelId -> fqn.modelId)
    val deleted: Int = db.command(command).execute(params.asJava)
    deleted match {
      case 1 => Unit
      case _ => throw new IllegalArgumentException("The model could not be deleted because it did not exist")
    }
  }

  def deleteAllModelsInCollection(collectionId: String): Try[Unit] = tryWithDb { db =>
    val queryString =
      "DELETE FROM Model WHERE collectionId = :collectionId"
    val command = new OCommandSQL(queryString)
    val params = Map(ModelStore.CollectionId -> collectionId)
    db.command(command).execute(params.asJava)
    Unit
  }

  def getModel(fqn: ModelFqn): Try[Option[Model]] = tryWithDb { db =>
    val queryString =
      """SELECT *
        |FROM Model
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(
      ModelStore.CollectionId -> fqn.collectionId,
      ModelStore.ModelId -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result) { _.asModel }
  }

  def getModelMetaData(fqn: ModelFqn): Try[Option[ModelMetaData]] = tryWithDb { db =>
    val queryString =
      """SELECT modelId, collectionId, version, createdTime, modifiedTime
        |FROM Model
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(ModelStore.CollectionId -> fqn.collectionId, ModelStore.ModelId -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result) { _.asModelMetaData }
  }

  def getAllModelMetaDataInCollection(
    collectionId: String,
    offset: Option[Int],
    limit: Option[Int]): Try[List[ModelMetaData]] = tryWithDb { db =>

    val queryString =
      """SELECT modelId, collectionId, version, createdTime, modifiedTime
         |FROM Model
         |WHERE
         |  collectionId = :collectionId
         |ORDER BY
         |  modelId ASC""".stripMargin

    val pagedQuery = QueryUtil.buildPagedQuery(
      queryString,
      limit,
      offset)

    val query = new OSQLSynchQuery[ODocument](pagedQuery)
    val params = Map(ModelStore.CollectionId -> collectionId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { _.asModelMetaData }
  }

  // TODO implement orderBy and ascending / descending
  def getAllModelMetaData(
    offset: Option[Int],
    limit: Option[Int]): Try[List[ModelMetaData]] = tryWithDb { db =>

    val queryString =
      """SELECT modelId, collectionId, version, createdTime, modifiedTime
        |FROM Model
        |ORDER BY
        |  collectionId ASC,
        |  modelId ASC""".stripMargin

    val pageQuery = QueryUtil.buildPagedQuery(
      queryString,
      limit,
      offset)

    val query = new OSQLSynchQuery[ODocument](pageQuery)
    val result: JavaList[ODocument] = db.command(query).execute()
    result.asScala.toList map { _.asModelMetaData }
  }

  def getModelData(fqn: ModelFqn): Try[Option[JValue]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT data FROM Model WHERE collectionId = :collectionId AND modelId = :modelId")
    val params = Map(ModelStore.CollectionId -> fqn.collectionId, ModelStore.ModelId -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result)(doc => parse(doc.toJSON()) \\ ModelStore.Data)
  }
}
