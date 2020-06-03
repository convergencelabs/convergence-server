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

package com.convergencelabs.convergence.server.db.data

import com.convergencelabs.convergence.server.datastore.SortOrder
import com.convergencelabs.convergence.server.datastore.domain.{CollectionPermissions, DomainPersistenceProvider, DomainSession, DomainUserField}
import com.convergencelabs.convergence.server.domain.{DomainUser, JwtAuthKey}
import com.convergencelabs.convergence.server.domain.model.{Collection, ModelOperation, ModelSnapshot}
import com.convergencelabs.convergence.server.domain.model.data._
import com.convergencelabs.convergence.server.domain.model.ot._
import grizzled.slf4j.Logging

import scala.util.Try

class DomainExporter(private[this] val persistence: DomainPersistenceProvider) extends Logging {

  def exportDomain(): Try[DomainScript] = {
    logger.debug("Exporting domain data")
    for {
      config <- exportConfig()
      jwtKeys <- exportJwtAuthKeys()
      users <- exportUsers()
      sessions <- exportSessions()
      collections <- exportCollections()
      models <- exportModels()
    } yield {
      logger.debug("Done importing domain data")
      DomainScript(config, Some(jwtKeys), Some(users), Some(sessions), Some(collections), Some(models))
    }
  }

  private[this] def exportConfig(): Try[SetDomainConfig] = {
    logger.debug("Getting domain configuration")
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
      _.data.map { domainUser =>
        // FIXME better error handling here
        val pwHash = persistence.userStore.getDomainUserPasswordHash(domainUser.username).get map { hash =>
          SetPassword("hash", hash)
        }
        val DomainUser(userType, username, firstName, lastName, displayName, email, lastLogin, disabled, deleted, deletedUsername) = domainUser
        CreateDomainUser(userType.toString.toLowerCase, username, firstName, lastName, displayName, email, lastLogin, disabled, deleted, deletedUsername, pwHash)
      }
    }
  }
  
  private[this] def exportSessions(): Try[List[CreateDomainSession]] = {
    logger.debug("Exporting domain sessions")
    persistence.sessionStore.getAllSessions(None, None) map {
      _.map { domainSession =>
        // FIXME better error handling here
        val DomainSession(id, userId, connected, disconnected, 
            authMethod, client, clientVersion, clientMetaData, remoteHost) = domainSession
        CreateDomainSession(id, userId.username, userId.userType.toString.toLowerCase, connected, disconnected, 
            authMethod, client, clientVersion, clientMetaData, remoteHost)
      }
    }
  }

  //FIXME: export permissions
  private[this] def exportCollections(): Try[List[CreateCollection]] = {
    logger.debug("exporting collections")
    persistence.collectionStore.getAllCollections(None, None, None) map {
      _.data.map { col =>
        val Collection(collectionId, name, overrideSnapshot, snapshotConfig, CollectionPermissions(true, true, true, true, true)) = col
        CreateCollection(collectionId, name, overrideSnapshot)
      }
    }
  }

  private[this] def exportModels(): Try[List[CreateModel]] = Try {
    logger.debug("exporting models")
    persistence.modelStore.getAllModelMetaData(None, None).map {
      modelList =>
        modelList.map(metaData => exportModel(metaData.id).get)
    }.get
  }

  private[this] def exportModel(modelId: String): Try[CreateModel] = {
    val models = persistence.modelStore
    val opStore = persistence.modelOperationStore
    val snapshotStore = persistence.modelSnapshotStore

    for {
      modelOpt <- models.getModel(modelId)
      ops <- opStore.getOperationsAfterVersion(modelId, 0)
      snapshots <- snapshotStore.getSnapshots(modelId)
    } yield {
      val model = modelOpt.get
      CreateModel(
        model.metaData.id,
        model.metaData.collection,
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
      case DateValue(vId, value) =>
        CreateDateValue(vId, value)
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
        
      case AppliedDateSetOperation(vId, noOp, value, oldValue) =>
        CreateDateSetOperation(vId, noOp, value, oldValue)
    }
  }
}