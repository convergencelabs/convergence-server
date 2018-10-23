package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.Date

import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.util.Failure
import scala.util.Try

import org.json4s.JsonAST.JObject
import org.parboiled2.ParseError

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.domain.mapper.DataValueMapper.ODocumentToDataValue
import com.convergencelabs.server.datastore.domain.mapper.ObjectValueMapper.ODocumentToObjectValue
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.ModelQueryResult
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.query.QueryParser
import com.convergencelabs.server.frontend.rest.DataValueToJValue
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging

object ModelStore {

  import com.convergencelabs.server.datastore.domain.schema.ModelClass._

  object Constants {
    val CollectionId = "collectionId"
  }

  private val FindModel = "SELECT * FROM Model WHERE id = :id"

  def getModelDocument(id: String, db: ODatabaseDocument): Try[ODocument] = {
    OrientDBUtil.getDocumentFromSingleValueIndex(db, Indices.Id, id)
  }

  private def findModelDocument(id: String, db: ODatabaseDocument): Try[Option[ODocument]] = {
    OrientDBUtil.findDocumentFromSingleValueIndex(db, Indices.Id, id)
  }

  def docToModelMetaData(doc: ODocument): ModelMetaData = {
    val createdTime: Date = doc.getProperty(Fields.CreatedTime)
    val modifiedTime: Date = doc.getProperty(Fields.ModifiedTime)
    val worldPermissions = ModelPermissionsStore.docToModelPermissions(doc.getProperty(Fields.WorldPermissions))
    ModelMetaData(
      doc.eval("collection.id").asInstanceOf[String],
      doc.getProperty(Fields.Id),
      doc.getProperty(Fields.Version),
      createdTime.toInstant(),
      modifiedTime.toInstant(),
      doc.getProperty(Fields.OverridePermissions),
      worldPermissions,
      doc.getProperty(Fields.ValuePrefix))
  }

  def docToModel(doc: ODocument): Model = {
    // TODO This can be cleaned up.. it seems like in some cases we are getting an ORecordId back
    // and in other cases an ODocument. This handles both cases.  We should figure out what
    // is supposed to come back and why it might be coming back as the other.
    val data: ODocument = doc.getProperty(Fields.Data).asInstanceOf[OIdentifiable].getRecord[ODocument];
    Model(docToModelMetaData(doc), data.asObjectValue)
  }

  def getModelRid(id: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Indices.Id, id)
  }
}

class ModelStore private[domain] (
  dbProvider: DatabaseProvider,
  operationStore: ModelOperationStore,
  snapshotStore: ModelSnapshotStore)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import ModelStore._
  import com.convergencelabs.server.datastore.domain.schema.ModelClass._

  def modelExists(id: String): Try[Boolean] = withDb { db =>
    val query = "SELECT count(*) as count FROM Model where id = :id"
    val params = Map("id" -> id)
    OrientDBUtil
      .getDocument(db, query, params)
      .map(_.getProperty("count").asInstanceOf[Long] > 0)
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
        val modelDoc: ODocument = db.newInstance(ClassName)
        modelDoc.setProperty(Fields.Collection, collectionRid)
        modelDoc.setProperty(Fields.Id, modelId)
        modelDoc.setProperty(Fields.Version, version)
        modelDoc.setProperty(Fields.CreatedTime, Date.from(createdTime))
        modelDoc.setProperty(Fields.ModifiedTime, Date.from(modifiedTime))
        modelDoc.setProperty(Fields.OverridePermissions, overrridePermissions)
        modelDoc.setProperty(Fields.ValuePrefix, valuePrefix)

        val worldPermsDoc = ModelPermissionsStore.modelPermissionToDoc(worldPermissions)
        modelDoc.setProperty(Fields.WorldPermissions, worldPermsDoc, OType.EMBEDDED)

        val dataDoc = OrientDataValueBuilder.objectValueToODocument(data, modelDoc)
        modelDoc.setProperty(Fields.Data, dataDoc, OType.LINK)

        // FIXME what about the user permissions LINKLIST?

        dataDoc.save()
        modelDoc.save()
        db.commit()
        ()
      }.get
  } recoverWith (handleDuplicateValue)

  //FIXME: Add in overridePermissions flag
  def updateModel(id: String, data: ObjectValue, worldPermissions: Option[ModelPermissions]): Try[Unit] = tryWithDb { db =>
    getModelDocument(id, db).flatMap { doc =>
      deleteDataValuesForModel(id).map { _ =>
        val dataValueDoc = OrientDataValueBuilder.dataValueToODocument(data, doc)
        doc.setProperty(Fields.Data, dataValueDoc)
        val worldPermissionsDoc = worldPermissions.map(ModelPermissionsStore.modelPermissionToDoc(_))
        doc.setProperty(Fields.WorldPermissions, worldPermissions)
        doc.save()
        ()
      }
    }
  }

  def updateModelOnOperation(id: String, version: Long, timestamp: Instant, db: Option[ODatabaseDocument] = None): Try[Unit] = withDb(db) { db =>
    val command = "UPDATE Model SET version = :version, modifiedTime = :modifiedTime WHERE id = :id"
    val params = Map(Fields.Id -> id, Fields.ModifiedTime -> Date.from(timestamp), Fields.Version -> version)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }

  //TODO: This should probably be handled in a model shutdown routine so that we only update it once with the final value
  def setNextPrefixValue(id: String, value: Long): Try[Unit] = withDb { db =>
    val command = "UPDATE Model SET valuePrefix = :valuePrefix WHERE id = :id"
    val params = Map(Fields.Id -> id, Fields.ValuePrefix -> value)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }

  def deleteModel(id: String): Try[Unit] = withDb { db =>
    for {
      _ <- operationStore.deleteAllOperationsForModel(id)
      _ <- snapshotStore.removeAllSnapshotsForModel(id)
      _ <- deleteDataValuesForModel(id)
      _ <- deleteModelRecord(id)
    } yield (())
  }

  def deleteModelRecord(id: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM Model WHERE id = :id"
    val params = Map(Fields.Id -> id)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }

  def deleteDataValuesForModel(id: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM DataValue WHERE model.id = :id"
    val params = Map(Fields.Id -> id)
    OrientDBUtil.command(db, command, params).map(_ => ())
  }

  def deleteAllModelsInCollection(collectionId: String): Try[Unit] = withDb { db =>
    operationStore.deleteAllOperationsForCollection(collectionId).flatMap { _ =>
      snapshotStore.removeAllSnapshotsForCollection(collectionId)
    }.flatMap { _ =>
      deleteDataValuesForCollection(collectionId, db)
    }.flatMap { _ =>
      val command = "DELETE FROM Model WHERE collection.id = :collectionId"
      val params = Map(Constants.CollectionId -> collectionId)
      OrientDBUtil.command(db, command, params).map(_ => ())
    }
  }

  def deleteDataValuesForCollection(collectionId: String, db: ODatabaseDocument): Try[Unit] = {
    val command = "DELETE FROM DataValue WHERE model.collection.id = :collectionId"
    val params = Map(Constants.CollectionId -> collectionId)
    OrientDBUtil.command(db, command, params).map(_ => ())
  }

  def getModel(id: String): Try[Option[Model]] = withDb { db =>
    ModelStore
      .findModelDocument(id, db)
      .map(_.map(ModelStore.docToModel(_)))
  }

  def getModelMetaData(id: String): Try[Option[ModelMetaData]] = withDb { db =>
    ModelStore
      .findModelDocument(id, db)
      .map(_.map(ModelStore.docToModelMetaData(_)))
  }

  def getAllModelMetaDataInCollection(
    collectionId: String,
    offset: Option[Int],
    limit: Option[Int]): Try[List[ModelMetaData]] = withDb { db =>

    val baseQuery = "SELECT FROM Model WHERE collection.id = :collectionId ORDER BY id ASC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    val params = Map(Constants.CollectionId -> collectionId)
    OrientDBUtil.queryAndMap(db, query, params)(docToModelMetaData(_))
  }

  // TODO implement orderBy and ascending / descending
  def getAllModelMetaData(
    offset: Option[Int],
    limit: Option[Int]): Try[List[ModelMetaData]] = withDb { db =>

    val baseQuery = "SELECT FROM Model ORDER BY collection.id ASC, id ASC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    OrientDBUtil.queryAndMap(db, query)(docToModelMetaData(_))
  }

  def queryModels(query: String, username: Option[String]): Try[List[ModelQueryResult]] = withDb { db =>
    new QueryParser(query).InputLine.run().recoverWith {
      case ParseError(position, principalPosition, traces) =>
        val index = position.index
        Failure(QueryParsingException(s"Parse error at position ${index}", query, Some(index)))
    }.flatMap { select =>
      val ModelQueryParameters(query, params, as) = ModelQueryBuilder.queryModels(select, username)
      OrientDBUtil.query(db, query, params).map { result =>
        if (select.fields.isEmpty) {
          result.map { modelDoc =>
            val model = ModelStore.docToModel(modelDoc)
            ModelQueryResult(model.metaData, DataValueToJValue.toJson(model.data))
          }
        } else {
          result.map { modelDoc =>
            val results = modelDoc.toMap()
            results.remove("@rid")
            val createdTime = results.remove(Fields.CreatedTime).asInstanceOf[Date]
            val modifiedTime = results.remove(Fields.ModifiedTime).asInstanceOf[Date]
            val meta = ModelMetaData(
              results.remove("collectionId").asInstanceOf[String],
              results.remove(Fields.Id).asInstanceOf[String],
              results.remove(Fields.Version).asInstanceOf[Long],
              createdTime.toInstant(),
              modifiedTime.toInstant(),
              false,
              ModelPermissions(false, false, false, false),
              results.remove(Fields.ValuePrefix).asInstanceOf[Long])

            val values = results.asScala.toList map Function.tupled { (field, value) =>
              (as.get(field).getOrElse(field), DataValueToJValue.toJson(value.asInstanceOf[ODocument].asDataValue))
            }
            ModelQueryResult(meta, JObject(values))
          }
        }
      }
    }
  }

  def getModelData(id: String): Try[Option[ObjectValue]] = withDb { db =>
    ModelStore
      .findModelDocument(id, db)
      .map(_.map(doc => doc.getProperty(Fields.Data).asInstanceOf[ODocument].asObjectValue))
  }

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Indices.Id =>
          Failure(DuplicateValueException(Fields.Id))
        case Indices.Collection_Id =>
          Failure(DuplicateValueException("collection, id"))
        case _ =>
          Failure(e)
      }
  }
}

case class QueryParsingException(message: String, query: String, index: Option[Int]) extends Exception(message)
