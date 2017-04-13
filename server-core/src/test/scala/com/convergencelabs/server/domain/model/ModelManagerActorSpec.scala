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
import org.mockito.Matchers
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.mock.MockitoSugar

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

class ModelManagerActorSpec
    extends TestKit(ActorSystem("ModelManagerActorSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  // FIXME we need to test that models actually get created and deleted.  Not sure how to do this.

  "A ModelManagerActor" when {
    "opening model" must {
      "sucessfully load an unopen model" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(SessionKey(userId1, sessionId1), modelFqn, true, client.ref), client.ref)

        val message = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
        assert(message.modelData == modelData.data)
        assert(message.metaData.version == modelData.metaData.version)
        assert(message.metaData.createdTime == modelData.metaData.createdTime)
        assert(message.metaData.modifiedTime == modelData.metaData.modifiedTime)
      }

      "sucessfully load an open model" in new TestFixture {
        val client1 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(SessionKey(userId1, sessionId1), modelFqn, true, client1.ref), client1.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        val client2 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(SessionKey(userId2, sessionId1), modelFqn, true, client2.ref), client2.ref)
        val response = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        assert(response.modelData == modelData.data)
        assert(response.metaData.version == modelData.metaData.version)
        assert(response.metaData.createdTime == modelData.metaData.createdTime)
        assert(response.metaData.modifiedTime == modelData.metaData.modifiedTime)
      }

      "request data if the model does not exist" in new TestFixture {
        val client1 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(SessionKey(userId1, sessionId1), nonExistentModelFqn, true, client1.ref), client1.ref)
        val response = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientModelDataRequest])
      }
    }

    "requested to create a model" must {
      "return ModelCreated if the model does not exist" in new TestFixture {
        val client = new TestProbe(system)
        val ModelFqn(cId, mId) = nonExistentModelFqn
        val data = ObjectValue("", Map())

        val now = Instant.now()

        Mockito.when(modelStore.createModel(cId, Some(mId), data, true, modelPermissions))
          .thenReturn(Success(Model(ModelMetaData(nonExistentModelFqn, 0L, now, now, true, modelPermissions), data)))

        Mockito.when(modelSnapshotStore.createSnapshot(Matchers.any()))
          .thenReturn(Success(()))

        modelManagerActor.tell(CreateModelRequest(SessionKey(userId1, sessionId1), cId, Some(mId), data, Some(true), Some(modelPermissions)), client.ref)
        client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ModelCreated(nonExistentModelFqn))
      }

      "return ModelAlreadyExists if the model exists" in new TestFixture {
        val client = new TestProbe(system)
        val ModelFqn(cId, mId) = modelFqn
        val data = ObjectValue("", Map())

        Mockito.when(modelStore.createModel(cId, Some(mId), data, true, modelPermissions))
          .thenReturn(Failure(DuplicateValueExcpetion("foo")))

        modelManagerActor.tell(CreateModelRequest(SessionKey(userId1, sessionId1), cId, Some(mId), data, Some(true), Some(modelPermissions)), client.ref)
        client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ModelAlreadyExists)
      }
    }

    "requested to delete a model" must {
      "return ModelDeleted if the model exists" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(DeleteModelRequest(SessionKey(userId1, sessionId1), modelFqn), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ModelDeleted)
      }

      "return ModelNotFound if the model does not exist" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(DeleteModelRequest(SessionKey(userId1, sessionId1), nonExistentModelFqn), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), akka.actor.Status.Failure(EntityNotFoundException()))
      }
    }
    
    "permissions are set to false" must {
      "throw exception on open" in new TestFixture {
        
      }
    }
    
  }

  trait TestFixture {
    val userId1 = "u1";
    val userId2 = "u2";
    val sessionId1 = "1";
    val collectionId = "collection"

    val noModelId = "no model"
    val nonExistentModelFqn = ModelFqn(collectionId, noModelId)
    val modelId = "model"
    val modelFqn = ModelFqn(collectionId, modelId)
    val modelJsonData = JObject("key" -> JString("value"))
    val modelCreateTime = Instant.ofEpochMilli(2L)
    val modelModifiedTime = Instant.ofEpochMilli(3L)
    val modelData = Model(ModelMetaData(modelFqn, 1L, modelCreateTime, modelModifiedTime, true, modelPermissions), ObjectValue("", Map()))
    val modelSnapshotTime = Instant.ofEpochMilli(2L)
    val modelSnapshotMetaData = ModelSnapshotMetaData(modelFqn, 1L, modelSnapshotTime)

    val modelStore = mock[ModelStore]
    Mockito.when(modelStore.modelExists(modelId)).thenReturn(Success(true))
    Mockito.when(modelStore.deleteModel(modelId)).thenReturn(Success(()))
    Mockito.when(modelStore.modelExists(noModelId)).thenReturn(Success(false))
    Mockito.when(modelStore.deleteModel(noModelId)).thenReturn(Failure(EntityNotFoundException()))
    Mockito.when(modelStore.getModel(modelId)).thenReturn(Success(Some(modelData)))

    val modelSnapshotStore = mock[ModelSnapshotStore]
    Mockito.when(modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelId)).thenReturn(Success(Some(modelSnapshotMetaData)))

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

    val modelPermissionsStore = mock[ModelPermissionsStore]
    Mockito.when(modelPermissionsStore.modelOverridesCollectionPermissions(modelId)).thenReturn(Success(true))
    Mockito.when(modelPermissionsStore.modelOverridesCollectionPermissions(noModelId)).thenReturn(Success(true))
    Mockito.when(modelPermissionsStore.getModelWorldPermissions(modelId)).thenReturn(Success(ModelPermissions(true, true, true, true)))
    Mockito.when(modelPermissionsStore.getModelWorldPermissions(noModelId)).thenReturn(Success(ModelPermissions(true, true, true, true)))
    Mockito.when(modelPermissionsStore.getCollectionWorldPermissions(collectionId)).thenReturn(Success(Some(CollectionPermissions(true, true, true, true, true))))
    Mockito.when(modelPermissionsStore.getAllModelUserPermissions(modelId)).thenReturn(Success(Map[String, ModelPermissions]()))
    Mockito.when(modelPermissionsStore.getAllModelUserPermissions(noModelId)).thenReturn(Success(Map[String, ModelPermissions]()))
    Mockito.when(modelPermissionsStore.getCollectionUserPermissions(collectionId, userId1)).thenReturn(Success(Some(CollectionPermissions(true, true, true, true, true))))
    Mockito.when(modelPermissionsStore.getCollectionUserPermissions(collectionId, userId2)).thenReturn(Success(Some(CollectionPermissions(true, true, true, true, true))))
    Mockito.when(modelPermissionsStore.getAllCollectionUserPermissions(collectionId)).thenReturn(Success(Map[String, CollectionPermissions]()))
    Mockito.when(modelPermissionsStore.getModelUserPermissions(modelId, userId1)).thenReturn(Success(Some(ModelPermissions(true, true, true, true))))
    Mockito.when(modelPermissionsStore.getModelUserPermissions(modelId, userId2)).thenReturn(Success(Some(ModelPermissions(true, true, true, true))))
    Mockito.when(modelPermissionsStore.getModelUserPermissions(noModelId, userId1)).thenReturn(Success(Some(ModelPermissions(true, true, true, true))))
    Mockito.when(modelPermissionsStore.getModelUserPermissions(noModelId, userId2)).thenReturn(Success(Some(ModelPermissions(true, true, true, true))))
    Mockito.when(modelPermissionsStore.updateModelUserPermissions(modelId, userId1, ModelPermissions(true, true, true, true))).thenReturn(Success(()))
    Mockito.when(modelPermissionsStore.updateModelUserPermissions(modelId, userId2, ModelPermissions(true, true, true, true))).thenReturn(Success(()))
    Mockito.when(modelPermissionsStore.updateModelUserPermissions(noModelId, userId1, ModelPermissions(true, true, true, true))).thenReturn(Success(()))
    Mockito.when(modelPermissionsStore.updateModelUserPermissions(noModelId, userId2, ModelPermissions(true, true, true, true))).thenReturn(Success(()))

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

    val props = ModelManagerActor.props(domainFqn, protocolConfig, persistenceManager)

    val modelManagerActor = system.actorOf(props, resourceId)
  }
}
