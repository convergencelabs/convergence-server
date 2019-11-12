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

package com.convergencelabs.convergence.server.domain.model

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.mockito.Matchers.any
import org.mockito.Matchers.{ eq => meq }
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.mockito.MockitoSugar
import org.scalatest.Matchers._

import com.convergencelabs.convergence.server.HeartbeatConfiguration
import com.convergencelabs.convergence.server.ProtocolConfiguration
import com.convergencelabs.convergence.server.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.datastore.domain.CollectionPermissions
import com.convergencelabs.convergence.server.datastore.domain.CollectionStore
import com.convergencelabs.convergence.server.datastore.domain.DomainConfigStore
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.convergence.server.datastore.domain.ModelOperationStore
import com.convergencelabs.convergence.server.datastore.domain.ModelPermissions
import com.convergencelabs.convergence.server.datastore.domain.ModelPermissionsStore
import com.convergencelabs.convergence.server.datastore.domain.ModelSnapshotStore
import com.convergencelabs.convergence.server.datastore.domain.ModelStore
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.ModelSnapshotConfig
import com.convergencelabs.convergence.server.domain.model.data.ObjectValue
import com.convergencelabs.convergence.server.util.MockDomainPersistenceManager

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe
import akka.actor.Status
import com.convergencelabs.convergence.server.domain.UnauthorizedException

class ModelLookupActorSpec
    extends TestKit(ActorSystem("ModelLookupActorSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  val timeout = FiniteDuration(1, TimeUnit.SECONDS)

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A ModelLookupActor" when {
    
  }

  trait TestFixture {
//    val userId1 = "u1";
//    val userId2 = "u2";
//    val sessionId1 = "1";
//
//    val u1Sk = SessionKey(userId1, sessionId1)
//
//    val collectionId = "collection"
//
//    val noModelId = "no model"
//    val existingModelId = "model"
//
//    val modelJsonData = JObject("key" -> JString("value"))
//    val modelCreateTime = Instant.ofEpochMilli(2L)
//    val modelModifiedTime = Instant.ofEpochMilli(3L)
//    val existingModel = Model(ModelMetaData(collectionId, existingModelId, 1L, modelCreateTime, modelModifiedTime, true, modelPermissions, 1), ObjectValue("", Map()))
//    val newModel = Model(ModelMetaData(collectionId, noModelId, 1L, modelCreateTime, modelModifiedTime, true, modelPermissions, 1), ObjectValue("", Map()))
//
//    val modelSnapshotTime = Instant.ofEpochMilli(2L)
//    val modelSnapshotMetaData = ModelSnapshotMetaData(existingModelId, 1L, modelSnapshotTime)
//
//    val modelStore = mock[ModelStore]
//    Mockito.when(modelStore.modelExists(existingModelId)).thenReturn(Success(true))
//    Mockito.when(modelStore.deleteModel(existingModelId)).thenReturn(Success(()))
//    Mockito.when(modelStore.modelExists(noModelId)).thenReturn(Success(false))
//    Mockito.when(modelStore.deleteModel(noModelId)).thenReturn(Failure(EntityNotFoundException()))
//    Mockito.when(modelStore.getModel(existingModelId)).thenReturn(Success(Some(existingModel)))
//
//    val modelSnapshotStore = mock[ModelSnapshotStore]
//    Mockito.when(modelSnapshotStore.getLatestSnapshotMetaDataForModel(existingModelId)).thenReturn(Success(Some(modelSnapshotMetaData)))
//
//    val modelOperationStore = mock[ModelOperationStore]
//
//    val domainConfigStore = mock[DomainConfigStore]
//    val snapshotConfig = ModelSnapshotConfig(
//      false,
//      true,
//      true,
//      250, // scalastyle:ignore magic.number
//      500, // scalastyle:ignore magic.number
//      false,
//      false,
//      Duration.of(0, ChronoUnit.MINUTES),
//      Duration.of(0, ChronoUnit.MINUTES))
//
//    Mockito.when(domainConfigStore.getModelSnapshotConfig()).thenReturn(Success(snapshotConfig))
//
//    val collectionStore = mock[CollectionStore]
//    Mockito.when(collectionStore.getOrCreateCollection(collectionId))
//      .thenReturn(Success(Collection(collectionId, "", false, snapshotConfig, CollectionPermissions(true, true, true, true, true))))
//
//    Mockito.when(collectionStore.ensureCollectionExists(collectionId))
//      .thenReturn(Success(()))
//
//    Mockito.when(collectionStore.collectionExists(collectionId))
//      .thenReturn(Success(true))
//
//    val modelPermissionsStore = mock[ModelPermissionsStore]
//    Mockito.when(modelPermissionsStore.updateModelUserPermissions(existingModelId, userId1, ModelPermissions(true, true, true, true))).thenReturn(Success(()))
//    Mockito.when(modelPermissionsStore.updateModelUserPermissions(existingModelId, userId2, ModelPermissions(true, true, true, true))).thenReturn(Success(()))
//    Mockito.when(modelPermissionsStore.updateModelUserPermissions(noModelId, userId1, ModelPermissions(true, true, true, true))).thenReturn(Success(()))
//    Mockito.when(modelPermissionsStore.updateModelUserPermissions(noModelId, userId2, ModelPermissions(true, true, true, true))).thenReturn(Success(()))
//
//    Mockito.when(modelPermissionsStore.updateAllModelUserPermissions(any(), any())).thenReturn(Success(()))
//
//    val domainPersistence = mock[DomainPersistenceProvider]
//    Mockito.when(domainPersistence.modelStore).thenReturn(modelStore)
//    Mockito.when(domainPersistence.modelSnapshotStore).thenReturn(modelSnapshotStore)
//    Mockito.when(domainPersistence.modelOperationStore).thenReturn(modelOperationStore)
//    Mockito.when(domainPersistence.configStore).thenReturn(domainConfigStore)
//    Mockito.when(domainPersistence.collectionStore).thenReturn(collectionStore)
//    Mockito.when(domainPersistence.modelPermissionsStore).thenReturn(modelPermissionsStore)
//
//    val domainFqn = DomainFqn("convergence", "default")
//
//    val modelPermissions = ModelPermissions(true, true, true, true)
//    val persistenceManager = new MockDomainPersistenceManager(Map(domainFqn -> domainPersistence))
//
//    val resourceId = "1" + System.nanoTime()
//    val domainActor = new TestProbe(system)
//    val protocolConfig = ProtocolConfiguration(
//      100 millis,
//      100 millis,
//      HeartbeatConfiguration(
//        true,
//        5 seconds,
//        10 seconds))
//
//    val modelPermissionsResolver = mock[ModelPermissionResolver]
//    Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
//      .thenReturn(Success(ModelPermissions(true, true, true, true)))
//
//    Mockito.when(modelPermissionsResolver.getModelAndCollectionPermissions(any(), any(), any()))
//      .thenReturn(Success(RealTimeModelPermissions(
//        false,
//        CollectionPermissions(true, true, true, true, true),
//        Map(),
//        ModelPermissions(true, true, true, true),
//        Map())))
//
//    val modelCreator = mock[ModelCreator]
//    Mockito.when(modelCreator.createModel(any(), any(), any(), meq(Some(existingModelId)), any(), any(), any(), any()))
//      .thenReturn(Failure(DuplicateValueException("")))
//    Mockito.when(modelCreator.createModel(any(), any(), any(), meq(Some(noModelId)), any(), any(), any(), any()))
//      .thenReturn(Success(newModel))
//
//    val props = ModelLookupActor.props(domainFqn, protocolConfig, persistenceManager, modelPermissionsResolver, modelCreator)
//
//    val modelManagerActor = system.actorOf(props, resourceId)
  }
}
