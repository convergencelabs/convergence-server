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

import java.time.{ Duration => JavaDuration }
import java.time.temporal.ChronoUnit

import scala.util.Try

import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.convergence.server.domain.DomainUser
import com.convergencelabs.convergence.server.domain.DomainUserType
import com.convergencelabs.convergence.server.domain.JwtAuthKey
import com.convergencelabs.convergence.server.domain.JwtKeyPair
import com.convergencelabs.convergence.server.domain.ModelSnapshotConfig
import com.convergencelabs.convergence.server.domain.model.Collection
import com.convergencelabs.convergence.server.domain.model.Model
import com.convergencelabs.convergence.server.domain.model.ModelMetaData
import com.convergencelabs.convergence.server.domain.model.ModelOperation
import com.convergencelabs.convergence.server.domain.model.ModelSnapshot
import com.convergencelabs.convergence.server.domain.model.ModelSnapshotMetaData
import com.convergencelabs.convergence.server.domain.model.data.ArrayValue
import com.convergencelabs.convergence.server.domain.model.data.BooleanValue
import com.convergencelabs.convergence.server.domain.model.data.DataValue
import com.convergencelabs.convergence.server.domain.model.data.DoubleValue
import com.convergencelabs.convergence.server.domain.model.data.NullValue
import com.convergencelabs.convergence.server.domain.model.data.ObjectValue
import com.convergencelabs.convergence.server.domain.model.data.StringValue
import com.convergencelabs.convergence.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedDiscreteOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedObjectSetOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedStringSetOperation

import grizzled.slf4j.Logging
import com.convergencelabs.convergence.server.datastore.domain.DomainSession
import com.convergencelabs.convergence.server.domain.model.NewModelOperation
import com.convergencelabs.convergence.server.domain.model.ot.AppliedDateSetOperation
import com.convergencelabs.convergence.server.domain.model.data.DateValue
import java.time.Duration
import com.sun.media.sound.ModelPerformer
import com.convergencelabs.convergence.server.datastore.domain.ModelPermissions
import com.convergencelabs.convergence.server.datastore.domain.CollectionPermissions
import com.convergencelabs.convergence.server.domain.DomainUserId

object DomainImporter {
  // FIXME we actually need to import / export this.
  // right now we are not exporting it, and hard coding this on the way in.
  val DefaultSnapshotConfig = ModelSnapshotConfig(
    false,
    false,
    false,
    1000,
    1000,
    false,
    false,
    Duration.ofMillis(600000),
    Duration.ofMillis(600000))
}

class DomainImporter(
  private[this] val persistence: DomainPersistenceProvider,
  private[this] val data: DomainScript) extends Logging {

  def importDomain(): Try[Unit] = {
    logger.debug("Importing domain data")
    setConfig() flatMap { _ =>
      createJwtAuthKeys()
    } flatMap { _ =>
      createUsers()
    } flatMap { _ =>
      createSessions()
    } flatMap { _ =>
      createCollections()
    } flatMap { _ =>
      createModels()
    } map { _ =>
      logger.debug("Done importing domain data")
    }
  }

  def setConfig(): Try[Unit] = {
    logger.debug("Setting domain configuration")

    val keyPair = JwtKeyPair(data.config.adminJwtKey.publicKey, data.config.adminJwtKey.privateKey)
    persistence.configStore.isInitialized() map {
      case true =>
        persistence.configStore.setAdminKeyPair(keyPair)
      case false =>
        // FIXME we need to abstract how the DomainDBController is doing this.
        val defaultSnapshotConfig = ModelSnapshotConfig(
          false,
          false,
          false,
          250,
          1000,
          false,
          false,
          JavaDuration.of(0, ChronoUnit.MINUTES),
          JavaDuration.of(0, ChronoUnit.MINUTES))
        persistence.configStore.initializeDomainConfig(keyPair, defaultSnapshotConfig, false)
    } flatMap { _ =>
      persistence.configStore.setAnonymousAuthEnabled(data.config.anonymousAuth)
    }
  }

  def createJwtAuthKeys(): Try[Unit] = Try {
    logger.debug("Importing JWT Auth Keys")
    data.jwtAuthKeys foreach (_.foreach { keyData =>
      val key = JwtAuthKey(
        keyData.id,
        keyData.description.getOrElse(""),
        keyData.updated,
        keyData.key,
        keyData.enabled)

      persistence.jwtAuthKeyStore.importKey(key).get
    })
  }

  def createUsers(): Try[Unit] = Try {
    logger.debug("Importting domain users")
    data.users foreach (_.foreach { userData =>
      val user = DomainUser(
        DomainUserType.withNameOpt(userData.userType).get,
        userData.username,
        userData.firstName,
        userData.lastName,
        userData.displayName,
        userData.email,
        userData.disabled,
        userData.deleted,
        userData.deletedUsername)
      persistence.userStore.createDomainUser(user)

      userData.password map { password =>
        password.passwordType match {
          case "hash" =>
            persistence.userStore.setDomainUserPasswordHash(userData.username, password.value)
          case "plaintext" =>
            persistence.userStore.setDomainUserPassword(userData.username, password.value)
        }
      }
    })
  }

  def createSessions(): Try[Unit] = Try {
    logger.debug("Importting domain sessions")
    data.sessions foreach (_.foreach { sessionData =>
      val CreateDomainSession(id, username, userType, connected, disconnected,
        authMethod, client, clientVersion, clientMetaData, remoteHost) = sessionData
      val session = DomainSession(id, DomainUserId(userType, username), connected, disconnected,
        authMethod, client, clientVersion, clientMetaData, remoteHost)
      persistence.sessionStore.createSession(session)
    })
  }

  //FIXME: import permissions
  def createCollections(): Try[Unit] = Try {
    logger.debug("Importting collections")
    data.collections foreach (_.foreach { collectionData =>
      val collection = Collection(
        collectionData.id,
        collectionData.name,
        false,
        DomainImporter.DefaultSnapshotConfig,
        CollectionPermissions(true, true, true, true, true))
      persistence.collectionStore.createCollection(collection).get
    })
  }

  def createModels(): Try[Unit] = Try {
    logger.debug("Importting models")
    data.models foreach (_.foreach(createModel(_)))
  }

  //FIXME: import permissions
  //FIXME: Add value prefix to data we import
  def createModel(modelData: CreateModel): Unit = {
    val data = createDataValue(modelData.data).asInstanceOf[ObjectValue]
    val model = Model(
      ModelMetaData(
        modelData.id,
        modelData.collection,
        modelData.version,
        modelData.created,
        modelData.modified,
        true,
        ModelPermissions(true, true, true, true),
        1),
      data)

    persistence.modelStore.createModel(model).get

    modelData.snapshots foreach (createModelSnapshot(modelData.id, _))
    modelData.operations foreach (createModelOperation(modelData.id, _))
  }

  def createDataValue(data: CreateDataValue): DataValue = {
    data match {
      case CreateObjectValue(vId, children) =>
        val converted = children map { case (k, v) => (k, createDataValue(v)) }
        ObjectValue(vId, converted)
      case CreateArrayValue(vId, children) =>
        val converted = children map (createDataValue(_))
        ArrayValue(vId, converted)
      case CreateDoubleValue(vId, value) =>
        DoubleValue(vId, value)
      case CreateStringValue(vId, value) =>
        StringValue(vId, value)
      case CreateBooleanValue(vId, value) =>
        BooleanValue(vId, value)
      case CreateNullValue(vId) =>
        NullValue(vId)
      case CreateDateValue(vId, value) =>
        DateValue(vId, value)
    }
  }

  def createModelSnapshot(id: String, snapshotData: CreateModelSnapshot): Unit = {
    val metaData = ModelSnapshotMetaData(id, snapshotData.version, snapshotData.timestamp)
    val data = createDataValue(snapshotData.data).asInstanceOf[ObjectValue]
    val snapshot = ModelSnapshot(metaData, data)
    persistence.modelSnapshotStore.createSnapshot(snapshot).get
  }

  def createModelOperation(id: String, opData: CreateModelOperation): Unit = {
    val op = createOperation(opData.op)
    val modelOp = NewModelOperation(
      id,
      opData.version,
      opData.timestamp,
      opData.sessionId,
      op)

    persistence.modelOperationStore.createModelOperation(modelOp).get
  }

  def createOperation(opData: CreateOperation): AppliedOperation = {
    opData match {
      case CreateCompoundOperation(operations) =>
        val ops = operations.map(createOperation(_).asInstanceOf[AppliedDiscreteOperation])
        AppliedCompoundOperation(ops)

      case CreateStringRemoveOperation(vId, noOp, index, length, oldValue) =>
        AppliedStringRemoveOperation(vId, noOp, index, length, oldValue)

      case CreateStringInsertOperation(vId, noOp, index, value) =>
        AppliedStringInsertOperation(vId, noOp, index, value)

      case CreateStringSetOperation(vId, noOp, value, oldValue) =>
        AppliedStringSetOperation(vId, noOp, value, oldValue)

      case CreateObjectSetPropertyOperation(vId, noOp, property, value, oldValue) =>
        AppliedObjectSetPropertyOperation(vId, noOp, property, createDataValue(value), oldValue map (createDataValue(_)))

      case CreateObjectAddPropertyOperation(vId, noOp, property, value) =>
        AppliedObjectAddPropertyOperation(vId, noOp, property, createDataValue(value))

      case CreateObjectRemovePropertyOperation(vId, noOp, property, oldValue) =>
        AppliedObjectRemovePropertyOperation(vId, noOp, property, oldValue map (createDataValue(_)))

      case CreateObjectSetOperation(vId, noOp, value, oldValue) =>
        val convertedValue = value map (x => (x._1, createDataValue(x._2)))
        val convertedOldValue = oldValue map { _.map(x => (x._1, createDataValue(x._2))) }
        AppliedObjectSetOperation(vId, noOp, convertedValue, convertedOldValue)

      case CreateNumberDeltaOperation(vId, noOp, value) =>
        AppliedNumberAddOperation(vId, noOp, value)

      case CreateNumberSetOperation(vId, noOp, value, oldValue) =>
        AppliedNumberSetOperation(vId, noOp, value, oldValue)

      case CreateBooleanSetOperation(vId, noOp, value, oldValue) =>
        AppliedBooleanSetOperation(vId, noOp, value, oldValue)

      case CreateArrayInsertOperation(vId, noOp, index, value) =>
        AppliedArrayInsertOperation(vId, noOp, index, createDataValue(value))

      case CreateArrayRemoveOperation(vId, noOp, index, oldValue) =>
        AppliedArrayRemoveOperation(vId, noOp, index, oldValue map (createDataValue(_)))

      case CreateArrayReplaceOperation(vId, noOp, index, value, oldValue) =>
        AppliedArrayReplaceOperation(vId, noOp, index, createDataValue(value), oldValue map (createDataValue(_)))

      case CreateArrayReorderOperation(vId, noOp, fromIndex, toIndex) =>
        AppliedArrayMoveOperation(vId, noOp, fromIndex, toIndex)

      case CreateArraySetOperation(vId, noOp, value, oldValue) =>
        AppliedArraySetOperation(vId, noOp, value map (createDataValue(_)), oldValue map (_.map(createDataValue(_))))

      case CreateDateSetOperation(vId, noOp, value, oldValue) =>
        AppliedDateSetOperation(vId, noOp, value, oldValue)
    }
  }
}