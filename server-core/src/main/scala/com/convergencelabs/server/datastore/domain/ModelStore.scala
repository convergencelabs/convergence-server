package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.Date
import java.util.{ List => JavaList }
import java.util.UUID

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.domain.mapper.ObjectValueMapper.ODocumentToObjectValue
import com.convergencelabs.server.datastore.domain.mapper.DataValueMapper.ODocumentToDataValue
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import ModelStore.Constants.CollectionId
import ModelStore.Fields._

import grizzled.slf4j.Logging
import com.convergencelabs.server.domain.model.query.QueryParser
import com.convergencelabs.server.domain.model.query.QueryParser
import com.convergencelabs.server.domain.model.ModelQueryResult
import com.convergencelabs.server.frontend.rest.DataValueToJValue
import org.json4s.JsonAST.JObject
import com.convergencelabs.server.frontend.realtime.ModelResult
import org.parboiled2.ParseError
import com.orientechnologies.orient.core.db.record.OIdentifiable

object ModelStore {
  val ModelClass = "Model"
  val ModelCollectionIdIndex = "Model.collection_id"
  val ModelIdIndex = "Model.id"

  object Constants {
    val CollectionId = "collectionId"
  }

  object Fields {
    val ModelClass = "Model"
    val Data = "data"
    val Collection = "collection"
    val Id = "id"
    val Version = "version"
    val CreatedTime = "createdTime"
    val ModifiedTime = "modifiedTime"
    val OverridePermissions = "overridePermissions"
    val WorldPermissions = "worldPermissions"
    val ValuePrefix = "valuePrefix"
  }

  private val FindModel = "SELECT * FROM Model WHERE id = :id"

  def getModelDocument(id: String, db: ODatabaseDocumentTx): Try[ODocument] = {
    val params = Map("id" -> id)
    QueryUtil.lookupMandatoryDocument(FindModel, params, db)
  }

  private def getModelDoc(id: String, db: ODatabaseDocumentTx): Option[ODocument] = {
    val params = Map("id" -> id)
    QueryUtil.lookupOptionalDocument(FindModel, params, db)
  }

  def docToModelMetaData(doc: ODocument): ModelMetaData = {
    val createdTime: Date = doc.field(CreatedTime, OType.DATETIME)
    val modifiedTime: Date = doc.field(ModifiedTime, OType.DATETIME)
    val worldPermissions = ModelPermissionsStore.docToModelPermissions(doc.field(WorldPermissions))
    ModelMetaData(
      doc.field("collection.id"),
      doc.field(Id),
      doc.field(Version, OType.LONG),
      createdTime.toInstant(),
      modifiedTime.toInstant(),
      doc.field(OverridePermissions),
      worldPermissions,
      doc.field(ValuePrefix))
  }

  def docToModel(doc: ODocument): Model = {
    // TODO This can be cleaned up.. it seems like in some cases we are getting an ORecordId back
    // and in other cases an ODocument. This handles both cases.  We should figure out what
    // is supposed to come back and why it might be coming back as the other.
    val data: ODocument = doc.field("data").asInstanceOf[OIdentifiable].getRecord[ODocument];
    Model(docToModelMetaData(doc), data.asObjectValue)
  }

  def getModelRid(id: String, db: ODatabaseDocumentTx): Try[ORID] = {
    QueryUtil.getRidFromIndex(ModelIdIndex, id, db)
  }
}

class ModelStore private[domain] (
  dbProvider: DatabaseProvider,
  operationStore: ModelOperationStore,
  snapshotStore: ModelSnapshotStore)
    extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  def modelExists(id: String): Try[Boolean] = tryWithDb { db =>
    val query = "SELECT id FROM Model where id = :id"
    val params = Map("id" -> id)
    QueryUtil.hasResults(query, params, db)
  }

  def createModel(
    modelId: String,
    collectionId: String,
    data: ObjectValue,
    overridePermissions: Boolean,
    worldPermissions: ModelPermissions): Try[Model] = {

    val createdTime = Instant.now()
    val modifiedTime = createdTime
    val version = 1
    val valuePrefix = 1

    val model = Model(
      ModelMetaData(
        collectionId,
        modelId,
        version,
        createdTime,
        modifiedTime,
        overridePermissions,
        worldPermissions,
        valuePrefix),
      data)

    this.createModel(model) map (_ => model)
  }

  def createModel(model: Model): Try[Unit] = tryWithDb { db =>
    val collectionId = model.metaData.collectionId
    val modelId = model.metaData.modelId
    val createdTime = model.metaData.createdTime
    val modifiedTime = model.metaData.modifiedTime
    val version = model.metaData.version
    val data = model.data
    val overrridePermissions = model.metaData.overridePermissions
    val worldPermissions = model.metaData.worldPermissions
    val valuePrefix = model.metaData.valuePrefix

    CollectionStore.getCollectionRid(collectionId, db)
      .recoverWith {
        case cause: Exception =>
          val message = s"Could not create model because collection ${collectionId} could not be found."
          logger.error(message, cause)
          Failure(new IllegalArgumentException(message))
      }.map { collectionRid =>
        db.begin()
        val modelDoc = new ODocument(ModelStore.ModelClass)
        modelDoc.field(Collection, collectionRid)
        modelDoc.field(Id, modelId)
        modelDoc.field(Version, version)
        modelDoc.field(CreatedTime, Date.from(createdTime))
        modelDoc.field(ModifiedTime, Date.from(modifiedTime))
        modelDoc.field(OverridePermissions, overrridePermissions)
        modelDoc.field(ValuePrefix, valuePrefix)

        val worldPermsDoc = ModelPermissionsStore.modelPermissionToDoc(worldPermissions)
        modelDoc.field(WorldPermissions, worldPermsDoc, OType.EMBEDDED)

        val dataDoc = OrientDataValueBuilder.objectValueToODocument(data, modelDoc)
        modelDoc.field(Data, dataDoc, OType.LINK)

        // FIXME what about the user permissions LINKLIST?

        dataDoc.save()
        modelDoc.save()
        db.commit()
        ()
      }.get
  } recoverWith {
    case e: ORecordDuplicatedException => handleDuplicateValue(e)
  }

  //FIXME: Add in overridePermissions flag
  def updateModel(id: String, data: ObjectValue, worldPermissions: Option[ModelPermissions]): Try[Unit] = tryWithDb { db =>
    ModelStore.getModelDoc(id, db) match {
      case Some(doc) =>
        deleteDataValuesForModel(id).map { _ =>
          val dataValueDoc = OrientDataValueBuilder.dataValueToODocument(data, doc)
          doc.field(Data, dataValueDoc)
          val worldPermissionsDoc = worldPermissions.map { ModelPermissionsStore.modelPermissionToDoc(_) }
          doc.field(WorldPermissions, worldPermissions)
          doc.save()
          ()
        }.get
      case None =>
        throw EntityNotFoundException()
    }
  }

  def updateModelOnOperation(id: String, timestamp: Instant): Try[Unit] = tryWithDb { db =>
    val queryString =
      """UPDATE Model SET
        |  version = eval('version + 1'),
        |  modifiedTime = :timestamp
        |WHERE id = :id""".stripMargin

    val updateCommand = new OCommandSQL(queryString)

    val params = Map(
      Id -> id,
      "timestamp" -> Date.from(timestamp))

    db.command(updateCommand).execute(params.asJava).asInstanceOf[Int] match {
      case 0 =>
        throw EntityNotFoundException()
      case _ =>
        ()
    }
  }

  //TODO: This should probably be handled in a model shutdown routine so that we only update it once with the final value 
  def setNextPrefixValue(id: String, value: Long): Try[Unit] = tryWithDb { db =>
    val queryString =
      """UPDATE Model SET
        |  valuePrefix = :valuePrefix
        |WHERE id = :id""".stripMargin

    val updateCommand = new OCommandSQL(queryString)

    val params = Map(Id -> id, ValuePrefix -> value)

    db.command(updateCommand).execute(params.asJava).asInstanceOf[Int] match {
      case 0 =>
        throw EntityNotFoundException()
      case _ =>
    }
  }

  def deleteModel(id: String): Try[Unit] = tryWithDb { db =>
    (for {
      _ <- operationStore.deleteAllOperationsForModel(id)
      _ <- snapshotStore.removeAllSnapshotsForModel(id)
      _ <- deleteDataValuesForModel(id)
      _ <- deleteModelRecord(id)
    } yield {
      ()
    }).get
  }

  def deleteModelRecord(id: String): Try[Unit] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM Model WHERE id = :id")
    val params = Map(Id -> id)
    db.command(command).execute(params.asJava).asInstanceOf[Int] match {
      case 1 =>
        ()
      case _ =>
        throw EntityNotFoundException()
    }
  }

  def deleteDataValuesForModel(id: String): Try[Unit] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM DataValue WHERE model.id = :id")
    val params = Map(Id -> id)
    db.command(command).execute(params.asJava).asInstanceOf[Int]
    ()
  }

  def deleteAllModelsInCollection(collectionId: String): Try[Unit] = tryWithDb { db =>
    operationStore.deleteAllOperationsForCollection(collectionId).flatMap { _ =>
      snapshotStore.removeAllSnapshotsForCollection(collectionId)
    }.flatMap { _ =>
      deleteDataValuesForCollection(collectionId, db)
    }.map { _ =>
      val queryString = "DELETE FROM Model WHERE collection.id = :collectionId"
      val command = new OCommandSQL(queryString)
      val params = Map(CollectionId -> collectionId)
      db.command(command).execute(params.asJava)
      ()
    }.get
  }

  def deleteDataValuesForCollection(collectionId: String, db: ODatabaseDocumentTx): Try[Unit] = Try {
    val command = new OCommandSQL("DELETE FROM DataValue WHERE model.collection.id = :collectionId")
    val params = Map(CollectionId -> collectionId)
    db.command(command).execute(params.asJava).asInstanceOf[Int]
    ()
  }

  def getModel(id: String): Try[Option[Model]] = tryWithDb { db =>
    ModelStore.getModelDoc(id, db) map (ModelStore.docToModel(_))
  }

  def getModelMetaData(id: String): Try[Option[ModelMetaData]] = tryWithDb { db =>
    ModelStore.getModelDoc(id, db) map (ModelStore.docToModelMetaData(_))
  }

  def getAllModelMetaDataInCollection(
    collectionId: String,
    offset: Option[Int],
    limit: Option[Int]): Try[List[ModelMetaData]] = tryWithDb { db =>

    val queryString =
      s"""SELECT *
         |FROM Model
         |WHERE
         |  collection.id = :collectionId
         |ORDER BY
         |  id ASC""".stripMargin

    val pagedQuery = QueryUtil.buildPagedQuery(
      queryString,
      limit,
      offset)

    val query = new OSQLSynchQuery[ODocument](pagedQuery)
    val params = Map(CollectionId -> collectionId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { ModelStore.docToModelMetaData(_) }
  }

  // TODO implement orderBy and ascending / descending
  def getAllModelMetaData(
    offset: Option[Int],
    limit: Option[Int]): Try[List[ModelMetaData]] = tryWithDb { db =>

    val queryString =
      s"""SELECT *
        |FROM Model
        |ORDER BY
        |  collection.id ASC,
        |  id ASC""".stripMargin

    val pageQuery = QueryUtil.buildPagedQuery(queryString, limit, offset)
    val query = new OSQLSynchQuery[ODocument](pageQuery)
    val result: JavaList[ODocument] = db.command(query).execute()
    result.asScala.toList map { ModelStore.docToModelMetaData(_) }
  }

  def queryModels(query: String, username: Option[String]): Try[List[ModelQueryResult]] = tryWithDb { db =>
    new QueryParser(query).InputLine.run().recoverWith {
      case ParseError(position, principalPosition, traces) =>
        val index = position.index
        Failure(QueryParsingException(s"Parse error at position ${index}", query, Some(index)))
    }.map { select =>
      val queryParams = ModelQueryBuilder.queryModels(select, username)
      val query = new OSQLSynchQuery[ODocument](queryParams.query)
      val result: JavaList[ODocument] = db.command(query).execute(queryParams.params.asJava)
      if (select.fields.isEmpty) {
        result.asScala.toList map { modelDoc =>
          val model = ModelStore.docToModel(modelDoc)
          ModelQueryResult(model.metaData, DataValueToJValue.toJson(model.data))
        }
      } else {
        result.asScala.toList map { modelDoc =>
          val results = modelDoc.toMap()
          results.remove("@rid")
          val createdTime = results.remove(CreatedTime).asInstanceOf[Date]
          val modifiedTime = results.remove(ModifiedTime).asInstanceOf[Date]
          val meta = ModelMetaData(
            results.remove("collectionId").asInstanceOf[String],
            results.remove(Id).asInstanceOf[String],
            results.remove(Version).asInstanceOf[Long],
            createdTime.toInstant(),
            modifiedTime.toInstant(),
            false,
            ModelPermissions(false, false, false, false),
            results.remove(ValuePrefix).asInstanceOf[Long])

          val values = results.asScala.toList map Function.tupled { (field, value) =>
            (queryParams.as.get(field).getOrElse(field),
              DataValueToJValue.toJson(value.asInstanceOf[ODocument].asDataValue))
          }
          ModelQueryResult(meta, JObject(values))
        }
      }
    }.get
  }

  def getModelData(id: String): Try[Option[ObjectValue]] = tryWithDb { db =>
    ModelStore.getModelDoc(id, db) map (doc => doc.field(Data).asInstanceOf[ODocument].asObjectValue)
  }

  private[this] def handleDuplicateValue[T](e: ORecordDuplicatedException): Try[T] = {
    e.getIndexName match {
      case ModelStore.ModelCollectionIdIndex =>
        Failure(DuplicateValueException("id_collection"))
      case _ =>
        Failure(e)
    }
  }
}

case class QueryParsingException(message: String, query: String, index: Option[Int]) extends Exception(message)
