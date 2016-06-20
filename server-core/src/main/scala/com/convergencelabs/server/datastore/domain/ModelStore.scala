package com.convergencelabs.server.datastore.domain

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.seqAsJavaListConverter
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
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.datastore.domain.mapper.ObjectValueMapper.ODocumentToObjectValue
import java.util.ArrayList
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

object ModelStore {
  private val ModelClass = "Model"
  private val Data = "data"
  private val CollectionId = "collectionId"
  private val ModelId = "modelId"
  private val Version = "version"
  private val CreatedTime = "createdTime"
  private val ModifiedTime = "modifiedTime"

  private val ModelIndex = "Model.collectionId_modelId"
}

class ModelStore private[domain] (dbPool: OPartitionedDatabasePool, operationStore: ModelOperationStore, snapshotStore: ModelSnapshotStore)
    extends AbstractDatabasePersistence(dbPool) {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def modelExists(fqn: ModelFqn): Try[Boolean] = tryWithDb { db =>
    val key = List(fqn.collectionId, fqn.modelId)
    Option(db
        .getMetadata
        .getIndexManager
        .getIndex(ModelStore.ModelIndex)
        .get(key.asJava))
        .isDefined
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
    operationStore.deleteAllOperationsForModel(fqn)
    snapshotStore.removeAllSnapshotsForModel(fqn)

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
    operationStore.deleteAllOperationsForCollection(collectionId)
    snapshotStore.removeAllSnapshotsForCollection(collectionId)

    val queryString =
      "DELETE FROM Model WHERE collectionId = :collectionId"
    val command = new OCommandSQL(queryString)
    val params = Map(ModelStore.CollectionId -> collectionId)
    db.command(command).execute(params.asJava)
    Unit
  }

  def getModel(fqn: ModelFqn): Try[Option[Model]] = tryWithDb { db =>
    getModelDoc(fqn, db) map (doc => doc.asModel)
  }

  def getModelMetaData(fqn: ModelFqn): Try[Option[ModelMetaData]] = tryWithDb { db =>
    getModelDoc(fqn, db) map (doc => doc.asModelMetaData)
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

  def getModelData(fqn: ModelFqn): Try[Option[ObjectValue]] = tryWithDb { db =>
    getModelDoc(fqn, db) map (doc => doc.field(ModelStore.Data).asInstanceOf[ODocument].asObjectValue)
  }

  private[this] def getModelDoc(fqn: ModelFqn, db: ODatabaseDocumentTx): Option[ODocument] = {
    val key = List(fqn.collectionId, fqn.modelId)
    val oId: OIdentifiable = db.getMetadata.getIndexManager.getIndex(ModelStore.ModelIndex).get(key.asJava).asInstanceOf[OIdentifiable]
    Option(oId) map (oId => oId.getRecord[ODocument])
  }
}
