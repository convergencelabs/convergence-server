package com.convergencelabs.server.domain.model

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
import org.scalatest.mock.MockitoSugar
import org.scalatest.Matchers._

import com.convergencelabs.server.HeartbeatConfiguration
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.DuplicateValueExcpetion
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.domain.CollectionStore
import com.convergencelabs.server.datastore.domain.DomainConfigStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.ModelOperationStore
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.util.MockDomainPersistenceManager

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe
import akka.actor.Status
import com.convergencelabs.server.domain.UnauthorizedException

class ModelManagerActorSpec
    extends TestKit(ActorSystem("ModelManagerActorSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  val timeout = FiniteDuration(1, TimeUnit.SECONDS)

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  // FIXME we need to test that models actually get created and deleted.  Not sure how to do this.

  "A ModelManagerActor" when {
    "opening model" must {
      "sucessfully load an unopen model" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(SessionKey(userId1, sessionId1), Some(existingModelId), Some(1), client.ref), client.ref)

        val message = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
        assert(message.modelData == existingModel.data)
        assert(message.metaData.version == existingModel.metaData.version)
        assert(message.metaData.createdTime == existingModel.metaData.createdTime)
        assert(message.metaData.modifiedTime == existingModel.metaData.modifiedTime)
      }

      "sucessfully load an open model" in new TestFixture {
        val client1 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(SessionKey(userId1, sessionId1), Some(existingModelId), Some(1), client1.ref), client1.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        val client2 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(SessionKey(userId2, sessionId1), Some(existingModelId), Some(1), client2.ref), client2.ref)
        val response = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        assert(response.modelData == existingModel.data)
        assert(response.metaData.version == existingModel.metaData.version)
        assert(response.metaData.createdTime == existingModel.metaData.createdTime)
        assert(response.metaData.modifiedTime == existingModel.metaData.modifiedTime)
      }

      "request data if the model does not exist" in new TestFixture {
        val client1 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(SessionKey(userId1, sessionId1), Some(noModelId), Some(1), client1.ref), client1.ref)
        val response = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientAutoCreateModelConfigRequest])
      }
    }

    "requested to create a model" must {
      "return ModelCreated if the model does not exist" in new TestFixture {
        val client = new TestProbe(system)
        val data = ObjectValue("", Map())

        val now = Instant.now()

        Mockito.when(modelStore.createModel(collectionId, Some(noModelId), data, true, modelPermissions))
          .thenReturn(Success(Model(ModelMetaData(collectionId, noModelId, 0L, now, now, true, modelPermissions), data)))

        Mockito.when(modelSnapshotStore.createSnapshot(any()))
          .thenReturn(Success(()))

        modelManagerActor.tell(CreateModelRequest(SessionKey(userId1, sessionId1), collectionId, Some(noModelId), data, Some(true), Some(modelPermissions), None), client.ref)
        client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), noModelId)
      }

      "return ModelAlreadyExistsException if the model exists" in new TestFixture {
        val client = new TestProbe(system)
        val data = ObjectValue("", Map())

        Mockito.when(modelStore.createModel(collectionId, Some(existingModelId), data, true, modelPermissions))
          .thenReturn(Failure(DuplicateValueExcpetion("foo")))

        modelManagerActor.tell(CreateModelRequest(SessionKey(userId1, sessionId1), collectionId, Some(existingModelId), data, Some(true), Some(modelPermissions), None), client.ref)
        client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelAlreadyExistsException(existingModelId)))
      }
    }

    "requested to delete a model" must {
      "return ModelDeleted if the model exists" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(DeleteModelRequest(SessionKey(userId1, sessionId1), existingModelId), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ModelDeleted())
      }

      "return ModelNotFoundException if the model does not exist" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(DeleteModelRequest(SessionKey(userId1, sessionId1), noModelId), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelNotFoundException(noModelId)))
      }
    }

    "getting permissions" must {
      "respond with a ModelNotFoundException is the model does not exists" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(GetModelPermissionsRequest(u1Sk, noModelId), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelNotFoundException(noModelId)))
      }

      "respond with a UnauthorizedException if the user doesn't have read permissions" in new TestFixture {
        Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
          .thenReturn(Success(ModelPermissions(false, true, true, true)))
        val client = new TestProbe(system)
        modelManagerActor.tell(GetModelPermissionsRequest(u1Sk, existingModelId), client.ref)
        val Status.Failure(cause) = client.expectMsgClass(timeout, classOf[Status.Failure])
        cause shouldBe a[UnauthorizedException]
      }

      "respond with a GetModelPermissionsResponse if the model exists and the user has read permissions" in new TestFixture {
        Mockito.when(modelPermissionsResolver.getModelPermissions(any(), any())).thenReturn(
            Success(ModelPemrissionResult(false, ModelPermissions(true, true, true, true), Map())))
            
        val client = new TestProbe(system)
        modelManagerActor.tell(GetModelPermissionsRequest(u1Sk, existingModelId), client.ref)
        val response = client.expectMsgClass(timeout, classOf[GetModelPermissionsResponse])
      }
    }

    "setting permissions" must {
      "respond with a ModelNotFoundException is the model does not exists" in new TestFixture {
        val client = new TestProbe(system)
        val message = SetModelPermissionsRequest(u1Sk, noModelId, None, None, false, Map())
        modelManagerActor.tell(message, client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelNotFoundException(noModelId)))
      }

      "respond with a UnauthorizedException if the user doesn't have manage permissions" in new TestFixture {
        Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
          .thenReturn(Success(ModelPermissions(true, true, true, false)))
        val client = new TestProbe(system)
        val message = SetModelPermissionsRequest(u1Sk, existingModelId, None, None, false, Map())
        modelManagerActor.tell(message, client.ref)
        val Status.Failure(cause) = client.expectMsgClass(timeout, classOf[Status.Failure])
        cause shouldBe a[UnauthorizedException]
      }

      "respond with () if the model exists and the user has manage permissions" in new TestFixture {
        val client = new TestProbe(system)
        val message = SetModelPermissionsRequest(u1Sk, existingModelId, None, None, false, Map())
        modelManagerActor.tell(message, client.ref)
        val response = client.expectMsg(timeout, ())
      }
    }
  }

  trait TestFixture {
    val userId1 = "u1";
    val userId2 = "u2";
    val sessionId1 = "1";

    val u1Sk = SessionKey(userId1, sessionId1)

    val collectionId = "collection"

    val noModelId = "no model"
    val existingModelId = "model"

    val modelJsonData = JObject("key" -> JString("value"))
    val modelCreateTime = Instant.ofEpochMilli(2L)
    val modelModifiedTime = Instant.ofEpochMilli(3L)
    val existingModel = Model(ModelMetaData(collectionId, existingModelId, 1L, modelCreateTime, modelModifiedTime, true, modelPermissions), ObjectValue("", Map()))
    val newModel = Model(ModelMetaData(collectionId, noModelId, 1L, modelCreateTime, modelModifiedTime, true, modelPermissions), ObjectValue("", Map()))

    val modelSnapshotTime = Instant.ofEpochMilli(2L)
    val modelSnapshotMetaData = ModelSnapshotMetaData(existingModelId, 1L, modelSnapshotTime)

    val modelStore = mock[ModelStore]
    Mockito.when(modelStore.modelExists(existingModelId)).thenReturn(Success(true))
    Mockito.when(modelStore.deleteModel(existingModelId)).thenReturn(Success(()))
    Mockito.when(modelStore.modelExists(noModelId)).thenReturn(Success(false))
    Mockito.when(modelStore.deleteModel(noModelId)).thenReturn(Failure(EntityNotFoundException()))
    Mockito.when(modelStore.getModel(existingModelId)).thenReturn(Success(Some(existingModel)))

    val modelSnapshotStore = mock[ModelSnapshotStore]
    Mockito.when(modelSnapshotStore.getLatestSnapshotMetaDataForModel(existingModelId)).thenReturn(Success(Some(modelSnapshotMetaData)))

    val modelOperationStore = mock[ModelOperationStore]

    val domainConfigStore = mock[DomainConfigStore]
    val snapshotConfig = ModelSnapshotConfig(
      false,
      true,
      true,
      250, // scalastyle:ignore magic.number
      500, // scalastyle:ignore magic.number
      false,
      false,
      Duration.of(0, ChronoUnit.MINUTES),
      Duration.of(0, ChronoUnit.MINUTES))

    Mockito.when(domainConfigStore.getModelSnapshotConfig()).thenReturn(Success(snapshotConfig))

    val collectionStore = mock[CollectionStore]
    Mockito.when(collectionStore.getOrCreateCollection(collectionId))
      .thenReturn(Success(Collection(collectionId, "", false, snapshotConfig, CollectionPermissions(true, true, true, true, true))))

    Mockito.when(collectionStore.ensureCollectionExists(collectionId))
      .thenReturn(Success(()))

    Mockito.when(collectionStore.collectionExists(collectionId))
      .thenReturn(Success(true))

    val modelPermissionsStore = mock[ModelPermissionsStore]
    Mockito.when(modelPermissionsStore.updateModelUserPermissions(existingModelId, userId1, ModelPermissions(true, true, true, true))).thenReturn(Success(()))
    Mockito.when(modelPermissionsStore.updateModelUserPermissions(existingModelId, userId2, ModelPermissions(true, true, true, true))).thenReturn(Success(()))
    Mockito.when(modelPermissionsStore.updateModelUserPermissions(noModelId, userId1, ModelPermissions(true, true, true, true))).thenReturn(Success(()))
    Mockito.when(modelPermissionsStore.updateModelUserPermissions(noModelId, userId2, ModelPermissions(true, true, true, true))).thenReturn(Success(()))

    Mockito.when(modelPermissionsStore.updateAllModelUserPermissions(any(), any())).thenReturn(Success(()))

    val domainPersistence = mock[DomainPersistenceProvider]
    Mockito.when(domainPersistence.modelStore).thenReturn(modelStore)
    Mockito.when(domainPersistence.modelSnapshotStore).thenReturn(modelSnapshotStore)
    Mockito.when(domainPersistence.modelOperationStore).thenReturn(modelOperationStore)
    Mockito.when(domainPersistence.configStore).thenReturn(domainConfigStore)
    Mockito.when(domainPersistence.collectionStore).thenReturn(collectionStore)
    Mockito.when(domainPersistence.modelPermissionsStore).thenReturn(modelPermissionsStore)

    val domainFqn = DomainFqn("convergence", "default")

    val modelPermissions = ModelPermissions(true, true, true, true)
    val persistenceManager = new MockDomainPersistenceManager(Map(domainFqn -> domainPersistence))

    val resourceId = "1" + System.nanoTime()
    val domainActor = new TestProbe(system)
    val protocolConfig = ProtocolConfiguration(
      100 millis,
      100 millis,
      HeartbeatConfiguration(
        true,
        5 seconds,
        10 seconds))

    val modelPermissionsResolver = mock[ModelPermissionResolver]
    Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
      .thenReturn(Success(ModelPermissions(true, true, true, true)))

    Mockito.when(modelPermissionsResolver.getModelAndCollectionPermissions(any(), any(), any()))
      .thenReturn(Success(RealTimeModelPermissions(
        false,
        CollectionPermissions(true, true, true, true, true),
        Map(),
        ModelPermissions(true, true, true, true),
        Map())))

    val modelCreator = mock[ModelCreator]
    Mockito.when(modelCreator.createModel(any(), any(), any(), meq(Some(existingModelId)), any(), any(), any(), any()))
      .thenReturn(Failure(DuplicateValueExcpetion("")))
    Mockito.when(modelCreator.createModel(any(), any(), any(), meq(Some(noModelId)), any(), any(), any(), any()))
      .thenReturn(Success(newModel))

    val props = ModelManagerActor.props(domainFqn, protocolConfig, persistenceManager, modelPermissionsResolver, modelCreator)

    val modelManagerActor = system.actorOf(props, resourceId)
  }
}
