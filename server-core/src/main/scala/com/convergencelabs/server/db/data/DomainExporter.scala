package com.convergencelabs.server.db.data

import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.JwtAuthKey
import com.convergencelabs.server.domain.model.Collection
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.ModelSnapshot
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.domain.DomainUserField
import com.convergencelabs.server.datastore.SortOrder

class DomainExporter(private[this] val persistence: DomainPersistenceProvider) extends Logging {

  def exportDomain(): Try[DomainScript] = {
    logger.debug("Importing domain data")
    for {
      config <- exportConfig()
      jwtKeys <- exportJwtAuthKeys()
      users <- exportUsers()
      collections <- exportCollections()
      models <- exportModels()
    } yield {
      logger.debug("Done importting domain data")
      DomainScript(config, Some(jwtKeys), Some(users), Some(collections), Some(models))
    }
  }

  private[this] def exportConfig(): Try[SetDomainConfig] = {
    logger.debug("Setting domain configuration")
    val config = persistence.configStore

    for {
      anonymous <- config.isAnonymousAuthEnabled()
      keyPair <- config.getAdminKeyPair()
    } yield {
      SetDomainConfig(anonymous, CreateJwtKeyPair(keyPair.publicKey, keyPair.privateKey))
    }
  }

  private[this] def exportJwtAuthKeys(): Try[List[CreateJwtAuthKey]] = {
    logger.debug("Exporting JWT Auth Keys")
    persistence.jwtAuthKeyStore.getKeys(None, None) map {
      _.map { jwtKey =>
        val JwtAuthKey(id, desc, updated, key, enabled) = jwtKey
        CreateJwtAuthKey(id, Some(desc), updated, key, enabled)
      }
    }
  }

  private[this] def exportUsers(): Try[List[CreateDomainUser]] = {
    logger.debug("Exporting domain users")
    persistence.userStore.getAllDomainUsers(Some(DomainUserField.Username), Some(SortOrder.Descending), None, None) map {
      _.map { domainUser =>
        // FIXME better error handling here
        val pwHash = persistence.userStore.getDomainUserPasswordHash(domainUser.username).get map { hash =>
          SetPassword("hash", hash)
        }
        val DomainUser(userType, username, firstName, lastName, displayName, email) = domainUser
        CreateDomainUser(userType.toString.toLowerCase, username, firstName, lastName, displayName, email, pwHash)
      }
    }
  }

  private[this] def exportCollections(): Try[List[CreateCollection]] = {
    logger.debug("exporting collections")
    persistence.collectionStore.getAllCollections(None, None) map {
      _.map { col =>
        val Collection(collectionId, name, overrideSnapshot, snapshotConfig) = col
        CreateCollection(collectionId, name, overrideSnapshot)
      }
    }
  }

  private[this] def exportModels(): Try[List[CreateModel]] = Try {
    logger.debug("exporting models")
    persistence.modelStore.getAllModelMetaData(None, None).map {
      modelList =>
        modelList.map(metaData => exportModel(metaData.fqn).get)
    }.get
  }

  private[this] def exportModel(fqn: ModelFqn): Try[CreateModel] = {
    val models = persistence.modelStore
    val opStore = persistence.modelOperationStore
    val snapshotStore = persistence.modelSnapshotStore

    for {
      modelOpt <- models.getModel(fqn)
      ops <- opStore.getOperationsAfterVersion(fqn, 0)
      snapshots <- snapshotStore.getSnapshots(fqn)
    } yield {
      val model = modelOpt.get
      CreateModel(
        model.metaData.fqn.collectionId,
        model.metaData.fqn.modelId,
        model.metaData.version,
        model.metaData.createdTime,
        model.metaData.modifiedTime,
        exportDataValue(model.data).asInstanceOf[CreateObjectValue],
        exportModelOperations(ops),
        exportModelSnapshots(snapshots))
    }
  }

  private[this] def exportDataValue(data: DataValue): CreateDataValue = {
    data match {
      case ObjectValue(vId, children) =>
        val converted = children map { case (k, v) => (k, exportDataValue(v)) }
        CreateObjectValue(vId, converted)
      case ArrayValue(vId, children) =>
        val converted = children map (exportDataValue(_))
        CreateArrayValue(vId, converted)
      case DoubleValue(vId, value) =>
        CreateDoubleValue(vId, value)
      case StringValue(vId, value) =>
        CreateStringValue(vId, value)
      case BooleanValue(vId, value) =>
        CreateBooleanValue(vId, value)
      case NullValue(vId) =>
        CreateNullValue(vId)
    }
  }

  private[this] def exportModelSnapshots(modelSnapshots: List[ModelSnapshot]): List[CreateModelSnapshot] = {
    modelSnapshots.map { snapshot =>
      CreateModelSnapshot(
        snapshot.metaData.version,
        snapshot.metaData.timestamp,
        exportDataValue(snapshot.data).asInstanceOf[CreateObjectValue])
    }
  }

  private[this] def exportModelOperations(ops: List[ModelOperation]): List[CreateModelOperation] = {
    ops.map { op =>
      CreateModelOperation(
        op.version,
        op.timestamp,
        op.username,
        op.sessionId,
        exportOperation(op.op))
    }
  }

  private[this] def exportOperation(opData: AppliedOperation): CreateOperation = {
    opData match {
      case AppliedCompoundOperation(operations) =>
        val ops = operations.map(exportOperation(_).asInstanceOf[CreateDiscreteOperation])
        CreateCompoundOperation(ops)

      case AppliedStringRemoveOperation(vId, noOp, index, length, oldValue) =>
        CreateStringRemoveOperation(vId, noOp, index, length, oldValue)

      case AppliedStringInsertOperation(vId, noOp, index, value) =>
        CreateStringInsertOperation(vId, noOp, index, value)

      case AppliedStringSetOperation(vId, noOp, value, oldValue) =>
        CreateStringSetOperation(vId, noOp, value, oldValue)

      case AppliedObjectSetPropertyOperation(vId, noOp, property, value, oldValue) =>
        CreateObjectSetPropertyOperation(vId, noOp, property, exportDataValue(value), oldValue map (exportDataValue(_)))

      case AppliedObjectAddPropertyOperation(vId, noOp, property, value) =>
        CreateObjectAddPropertyOperation(vId, noOp, property, exportDataValue(value))

      case AppliedObjectRemovePropertyOperation(vId, noOp, property, oldValue) =>
        CreateObjectRemovePropertyOperation(vId, noOp, property, oldValue map (exportDataValue(_)))

      case AppliedObjectSetOperation(vId, noOp, value, oldValue) =>
        val convertedValue = value map (x => (x._1, exportDataValue(x._2)))
        val convertedOldValue = oldValue map { _.map(x => (x._1, exportDataValue(x._2))) }
        CreateObjectSetOperation(vId, noOp, convertedValue, convertedOldValue)

      case AppliedNumberAddOperation(vId, noOp, value) =>
        CreateNumberDeltaOperation(vId, noOp, value)

      case AppliedNumberSetOperation(vId, noOp, value, oldValue) =>
        CreateNumberSetOperation(vId, noOp, value, oldValue)

      case AppliedBooleanSetOperation(vId, noOp, value, oldValue) =>
        CreateBooleanSetOperation(vId, noOp, value, oldValue)

      case AppliedArrayInsertOperation(vId, noOp, index, value) =>
        CreateArrayInsertOperation(vId, noOp, index, exportDataValue(value))

      case AppliedArrayRemoveOperation(vId, noOp, index, oldValue) =>
        CreateArrayRemoveOperation(vId, noOp, index, oldValue map (exportDataValue(_)))

      case AppliedArrayReplaceOperation(vId, noOp, index, value, oldValue) =>
        CreateArrayReplaceOperation(vId, noOp, index, exportDataValue(value), oldValue map (exportDataValue(_)))

      case AppliedArrayMoveOperation(vId, noOp, fromIndex, toIndex) =>
        CreateArrayReorderOperation(vId, noOp, fromIndex, toIndex)

      case AppliedArraySetOperation(vId, noOp, value, oldValue) =>
        CreateArraySetOperation(vId, noOp, value map (exportDataValue(_)), oldValue map (_.map(exportDataValue(_))))
    }
  }
}