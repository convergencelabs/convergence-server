/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain.model

import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.datastore.domain.collection.CollectionStore
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DataValueMapper._
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectValueMapper.ODocumentToObjectValue
import com.convergencelabs.convergence.server.backend.datastore.domain.model.query.Ast.SelectStatement
import com.convergencelabs.convergence.server.backend.datastore.domain.model.query.{ModelQueryBuilder, ModelQueryParameters, QueryParser}
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, DuplicateValueException, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.services.domain.model.ModelQueryResult
import com.convergencelabs.convergence.server.model.domain
import com.convergencelabs.convergence.server.model.domain.model
import com.convergencelabs.convergence.server.model.domain.model.{Model, ModelMetaData, ModelPermissions, ObjectValue}
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.util.{DataValueToJValue, QueryLimit, QueryOffset}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging
import org.json4s.JsonAST.JObject
import org.parboiled2.ParseError

import java.time.Instant
import java.util.Date
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

/**
 * The [[ModelStore]] is responsible for the persistence of RealTimeModels
 * in the system.
 *
 * @param dbProvider     The DatabaseProvider that provides a connection to
 *                       the database.
 * @param operationStore The  store that stores model operations.
 * @param snapshotStore  The store that manages model snapshots.
 */
class ModelStore private[domain](dbProvider: DatabaseProvider,
                                 operationStore: ModelOperationStore,
                                 snapshotStore: ModelSnapshotStore)
  extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import ModelStore._
  import com.convergencelabs.convergence.server.backend.datastore.domain.schema.ModelClass._

  def modelExists(id: String): Try[Boolean] = withDb { db =>
    val params = Map("id" -> id)
    OrientDBUtil
      .getDocument(db, ModelExistsQuery, params)
      .map(_.getProperty("count").asInstanceOf[Long] > 0)
  }

  private[this] val ModelExistsQuery = "SELECT count(*) as count FROM Model WHERE id = :id"

  def createModel(modelId: String,
                  collectionId: String,
                  data: ObjectValue,
                  createdTime: Option[Instant],
                  overridePermissions: Boolean,
                  worldPermissions: ModelPermissions): Try[Model] = {

    val created = createdTime.getOrElse(Instant.now())
    val modifiedTime = created
    val version = 1
    val valuePrefix = 1

    val model = domain.model.Model(
      domain.model.ModelMetaData(
        modelId,
        collectionId,
        version,
        created,
        modifiedTime,
        overridePermissions,
        worldPermissions,
        valuePrefix),
      data)

    this.createModel(model) map (_ => model)
  }

  def createModel(model: Model): Try[Unit] = tryWithDb { db =>
    val collectionId = model.metaData.collection
    val modelId = model.metaData.id
    val createdTime = model.metaData.createdTime
    val modifiedTime = model.metaData.modifiedTime
    val version = model.metaData.version
    val data = model.data
    val overridePermissions = model.metaData.overridePermissions
    val worldPermissions = model.metaData.worldPermissions
    val valuePrefix = model.metaData.valuePrefix

    CollectionStore.getCollectionRid(collectionId, db)
      .recoverWith {
        case cause: Exception =>
          val message = s"Could not create model because collection '$collectionId' could not be found."
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
      modelDoc.setProperty(Fields.OverridePermissions, overridePermissions)
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
  } recoverWith handleDuplicateValue

  //FIXME: Add in overridePermissions flag
  def updateModel(id: String, data: ObjectValue, worldPermissions: Option[ModelPermissions]): Try[Unit] = withDb { db =>

    for {
      _ <- Try(db.begin())
      currentDoc <- getModelDocument(id, db)
      _ <- deleteDataValuesForModel(id, Some(db))
      _ <- Try {
        val dataValueDoc = OrientDataValueBuilder.dataValueToODocument(data, currentDoc)
        currentDoc.setProperty(Fields.Data, dataValueDoc)

        worldPermissions.foreach(wp => {
          val worldPermissionsDoc = ModelPermissionsStore.modelPermissionToDoc(wp)
          currentDoc.setProperty(Fields.WorldPermissions, worldPermissionsDoc, OType.EMBEDDED)
        })

        dataValueDoc.save()
        currentDoc.save()
        db.commit()
        ()
      }
    } yield ()
  }

  def updateModelOnOperation(id: String, version: Long, timestamp: Instant, db: Option[ODatabaseDocument] = None): Try[Unit] = withDb(db) { db =>
    val params = Map(Fields.Id -> id, Fields.ModifiedTime -> Date.from(timestamp), Fields.Version -> version)
    OrientDBUtil.mutateOneDocument(db, UpdateModelOnOperationCommand, params)
  }

  private[this] val UpdateModelOnOperationCommand = "UPDATE Model SET version = :version, modifiedTime = :modifiedTime WHERE id = :id"

  def setNextPrefixValue(id: String, value: Long): Try[Unit] = withDb { db =>
    val command = "UPDATE Model SET valuePrefix = :valuePrefix WHERE id = :id"
    val params = Map(Fields.Id -> id, Fields.ValuePrefix -> value)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }


  def getAndIncrementNextValuePrefix(id: String): Try[Long] = withDb { db =>
    val params = Map(Fields.Id -> id)
    OrientDBUtil
      .singleResultCommand(db, GetAndIncrementValueIdPrefixCommand, params)
      .map(doc => doc.getProperty(Fields.ValuePrefix).asInstanceOf[Long])
  }

  private[this] val GetAndIncrementValueIdPrefixCommand = "UPDATE Model SET valuePrefix = valuePrefix + 1 RETURN AFTER valuePrefix WHERE id = :id"

  def deleteModel(id: String): Try[Unit] = withDb { db =>
    for {
      _ <- operationStore.deleteAllOperationsForModel(id, Some(db))
      _ <- snapshotStore.removeAllSnapshotsForModel(id, Some(db))
      _ <- deleteDataValuesForModel(id, Some(db))
      _ <- deleteModelRecord(id, Some(db))
    } yield ()
  }


  def deleteModelRecord(id: String, db: Option[ODatabaseDocument] = None): Try[Unit] = withDb(db) { db =>
    val params = Map(Fields.Id -> id)
    OrientDBUtil.mutateOneDocument(db, DeleteModelByIdCommand, params)
  }

  private[this] val DeleteModelByIdCommand = "DELETE FROM Model WHERE id = :id"


  def deleteDataValuesForModel(id: String, db: Option[ODatabaseDocument] = None): Try[Unit] = withDb(db) { db =>
    val params = Map(Fields.Id -> id)
    OrientDBUtil.commandReturningCount(db, DeleteDataValuesForModelCommand, params).map(_ => ())
  }

  private[this] val DeleteDataValuesForModelCommand = "DELETE FROM DataValue WHERE model.id = :id"

  def getModel(id: String): Try[Option[Model]] = withDb { db =>
    ModelStore
      .findModelDocument(id, db)
      .map(_.map(ModelStore.docToModel))
  }

  def getModelIfNewer(id: String, currentVersion: Long): Try[Option[Model]] = withDb { db =>
    val query = "SELECT FROM Model WHERE id = :id AND version > :version"
    val params = Map(Fields.Id -> id, Fields.Version -> currentVersion)
    OrientDBUtil.findDocumentAndMap(db, query, params)(docToModel)
  }

  def getModelMetaData(id: String): Try[Option[ModelMetaData]] = withDb { db =>
    ModelStore
      .findModelDocument(id, db)
      .map(_.map(ModelStore.docToModelMetaData))
  }

  def getAllModelMetaDataInCollection(collectionId: String,
                                      offset: QueryOffset,
                                      limit: QueryLimit): Try[List[ModelMetaData]] = withDb { db =>
    val baseQuery = "SELECT FROM Model WHERE collection.id = :collectionId ORDER BY id ASC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    val params = Map(Params.CollectionId -> collectionId)
    OrientDBUtil.queryAndMap(db, query, params)(docToModelMetaData)
  }

  // TODO implement orderBy and ascending / descending
  // FIXME Paged Data
  def getAllModelMetaData(offset: QueryOffset,
                          limit: QueryLimit): Try[List[ModelMetaData]] = withDb { db =>
    val baseQuery = "SELECT FROM Model ORDER BY collection.id ASC, id ASC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    OrientDBUtil.queryAndMap(db, query)(docToModelMetaData)
  }

  def queryModels(query: String, userId: Option[DomainUserId]): Try[PagedData[ModelQueryResult]] = withDb { db =>
    for {
      select <- new QueryParser(query).InputLine.run().recoverWith {
        case ParseError(position, _, _) =>
          val index = position.index
          Failure(QueryParsingException(s"Parse error at position $index", query, Some(index)))
      }
      count <- this.modelCountQuery(select, userId, db)
      results <- this.modelDataQuery(select, userId, db)
    } yield {
      PagedData[ModelQueryResult](results, select.offset.getOrZero, count)
    }
  }

  private[this] def modelCountQuery(select: SelectStatement, userId: Option[DomainUserId], db: ODatabaseDocument): Try[Long] = {
    val ModelQueryParameters(query, params, _) = ModelQueryBuilder.countModels(select, userId)
    OrientDBUtil.getDocument(db, query, params).map { result =>
      result.getProperty("count").asInstanceOf[Long]
    }
  }

  private[this] def modelDataQuery(select: SelectStatement, userId: Option[DomainUserId], db: ODatabaseDocument): Try[List[ModelQueryResult]] = {
    val ModelQueryParameters(query, params, as) = ModelQueryBuilder.queryModels(select, userId)
    OrientDBUtil.query(db, query, params).map { result =>
      if (select.fields.isEmpty) {
        result.map { modelDoc =>
          val model = ModelStore.docToModel(modelDoc)
          ModelQueryResult(model.metaData, DataValueToJValue.toJson(model.data).asInstanceOf[JObject])
        }
      } else {
        result.map { modelDoc =>
          val results = modelDoc.toMap
          results.remove("@rid")
          val createdTime = results.remove(Fields.CreatedTime).asInstanceOf[Date]
          val modifiedTime = results.remove(Fields.ModifiedTime).asInstanceOf[Date]
          val meta = model.ModelMetaData(
            results.remove(Fields.Id).asInstanceOf[String],
            results.remove("collectionId").asInstanceOf[String],
            results.remove(Fields.Version).asInstanceOf[Long],
            createdTime.toInstant,
            modifiedTime.toInstant,
            overridePermissions = false,
            ModelPermissions(read = false, write = false, remove = false, manage = false),
            results.remove(Fields.ValuePrefix).asInstanceOf[Long])

          val values = results.asScala.toList
            .filter { case (_, v) => v != null }
            .map(Function.tupled { (field, value) =>
              (as.getOrElse(field, field), DataValueToJValue.toJson(oDocumentToDataValue(value.asInstanceOf[ODocument])))
            })
          ModelQueryResult(meta, JObject(values))
        }
      }
    }
  }

  def getModelData(id: String): Try[Option[ObjectValue]] = withDb { db =>
    ModelStore
      .findModelDocument(id, db)
      .map(_.map(doc => doc.getProperty(Fields.Data).asInstanceOf[ODocument].asObjectValue))
  }

  private[this] val GetModelCountQuery = "SELECT count(*) as count FROM Model"

  def getModelCount(): Try[Long] = withDb { db =>
    OrientDBUtil.getDocument(db, GetModelCountQuery).map(_.getProperty("count").asInstanceOf[Long])
  }

  private[this] def handleDuplicateValue[T]: PartialFunction[Throwable, Try[T]] = {
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

  def deleteAllModelsInCollection(collectionId: String): Try[Unit] = withDb { db =>
    ModelStore.deleteAllModelsInCollection(collectionId, db)
  }
}


object ModelStore {

  import com.convergencelabs.convergence.server.backend.datastore.domain.schema.ModelClass._

  object Params {
    val CollectionId = "collectionId"
  }

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
    model.ModelMetaData(
      doc.getProperty(Fields.Id),
      doc.eval("collection.id").asInstanceOf[String],
      doc.getProperty(Fields.Version),
      createdTime.toInstant,
      modifiedTime.toInstant,
      doc.getProperty(Fields.OverridePermissions),
      worldPermissions,
      doc.getProperty(Fields.ValuePrefix))
  }

  private def docToModel(doc: ODocument): Model = {
    // TODO This can be cleaned up.. it seems like in some cases we are getting an ORecordId back
    //  and in other cases an ODocument. This handles both cases.  We should figure out what
    //  is supposed to come back and why it might be coming back as the other.
    val data: ODocument = doc.getProperty(Fields.Data).asInstanceOf[OIdentifiable].getRecord[ODocument]
    model.Model(docToModelMetaData(doc), data.asObjectValue)
  }

  def getModelRid(id: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Indices.Id, id)
  }

  def deleteAllModelsInCollection(collectionId: String, db: ODatabaseDocument): Try[Unit] = {
    for {
      _ <- ModelOperationStore.deleteAllOperationsForCollection(collectionId, db)
      _ <- ModelSnapshotStore.removeAllSnapshotsForCollection(collectionId, db)
      _ <- deleteDataValuesForCollection(collectionId, db)
      _ <- {
        val command = "DELETE FROM Model WHERE collection.id = :collectionId"
        val params = Map(Params.CollectionId -> collectionId)
        OrientDBUtil.commandReturningCount(db, command, params)
      }
    } yield ()
  }

  def deleteDataValuesForCollection(collectionId: String, db: ODatabaseDocument): Try[Unit] = {
    val command = "DELETE FROM DataValue WHERE model.collection.id = :collectionId"
    val params = Map(Params.CollectionId -> collectionId)
    OrientDBUtil.commandReturningCount(db, command, params).map(_ => ())
  }
}
