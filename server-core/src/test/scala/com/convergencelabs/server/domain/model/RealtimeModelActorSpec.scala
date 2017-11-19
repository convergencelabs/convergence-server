package com.convergencelabs.server.domain.model

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.util.Success

import org.mockito.ArgumentCaptor
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.mock.MockitoSugar
import org.scalatest.Matchers._

import com.convergencelabs.server.UnknownErrorResponse
import com.convergencelabs.server.datastore.domain.ModelOperationProcessor
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore
import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.CollectionStore
import com.convergencelabs.server.util.MockDomainPersistenceProvider
import com.convergencelabs.server.util.MockDomainPersistenceProvider
import com.convergencelabs.server.util.MockDomainPersistenceManager
import akka.actor.Status
import akka.cluster.sharding.ShardRegion.Passivate

// scalastyle:off magic.number
class RealtimeModelActorSpec
    extends TestKit(ActorSystem("RealtimeModelActorSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A RealtimeModelActor" when {
    "opening a closed model" must {
      "load the model from the database if it is persisted" in new MockDatabaseWithModel {
        val client = new TestProbe(system)
        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client.ref), client.ref)

        val message = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        assert(message.modelData == modelData.data)
        assert(message.metaData.version == modelData.metaData.version)
        assert(message.metaData.createdTime == modelData.metaData.createdTime)
        assert(message.metaData.modifiedTime == modelData.metaData.modifiedTime)
      }

      "notify openers of an initialization errror" in new MockDatabaseWithModel {
        val client = new TestProbe(system)

        // Set the database up to bomb
        Mockito.when(persistenceProvider.modelStore.getModel(Matchers.any())).thenThrow(new IllegalArgumentException("Induced error for test"))

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client.ref), client.ref)
        val message = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[UnknownErrorResponse])
      }

      "ask all connecting clients for state if it is not persisted" in new MockDatabaseWithoutModel {
        val client1 = new TestProbe(system)
        val client2 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
        val dataRequest1 = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientAutoCreateModelConfigRequest])
        assert(dataRequest1.autoConfigId == 1)

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), SessionKey(uid2, session2), client2.ref), client2.ref)
        val dataRequest2 = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientAutoCreateModelConfigRequest])
        assert(dataRequest1.autoConfigId == 1)
      }

      "reject a client that does not respond with data" in new MockDatabaseWithoutModel {
        val client1 = new TestProbe(system)
        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientAutoCreateModelConfigRequest])
        val ClientDataRequestFailure(message) = client1.expectMsgClass(FiniteDuration(200, TimeUnit.MILLISECONDS), classOf[ClientDataRequestFailure])
      }

      "reject a client that responds with the wrong message in request to data" in new MockDatabaseWithoutModel {
        val client1 = new TestProbe(system)
        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientAutoCreateModelConfigRequest])
        client1.reply("some object") // Any message that is not a ClientModelDataResponse will do here.
        client1.expectMsgClass(FiniteDuration(200, TimeUnit.MILLISECONDS), classOf[UnknownErrorResponse])
      }

      "notify all queued clients when data is returned by the first client" in new MockDatabaseWithoutModel {
        val client1 = new TestProbe(system)
        val client2 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientAutoCreateModelConfigRequest])

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), SessionKey(uid2, session2), client2.ref), client2.ref)
        client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientAutoCreateModelConfigRequest])

        // Now mock that the data is there.
        val now = Instant.now()
        Mockito.when(persistenceProvider.modelStore.createModel(collectionId, modelId, modelJsonData, true, modelPermissions))
          .thenReturn(Success(Model(ModelMetaData(collectionId, modelId, 0L, now, now, true, modelPermissions, 1), modelJsonData)))
        Mockito.when(persistenceProvider.modelSnapshotStore.createSnapshot(Matchers.any())).thenReturn(Success(()))
        Mockito.when(persistenceProvider.modelStore.getModel(modelId)).thenReturn(Success(Some(modelData)))
        Mockito.when(persistenceProvider.modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelId)).thenReturn(Success(Some(modelSnapshotMetaData)))

        client1.reply(ClientAutoCreateModelConfigResponse(collectionId, Some(modelJsonData), Some(true), Some(modelPermissions), None, None))
        client2.reply(ClientAutoCreateModelConfigResponse(collectionId, Some(modelJsonData), Some(true), Some(modelPermissions), None, None))

        // Verify that both clients got the data.
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
        val openResponse = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        assert(openResponse.modelData == modelJsonData)
        assert(openResponse.metaData.version == modelData.metaData.version)
        assert(openResponse.metaData.createdTime == modelData.metaData.createdTime)
        assert(openResponse.metaData.modifiedTime == modelData.metaData.modifiedTime)

        // Verify that the model and snapshot were created.
        // FIXME use ary capture to match it.
        verify(modelCreator, times(1)).createModel(any(), any(), any(), any(), any(), any(), any(), any())
      }
    }

    "opening an open model" must {
      "not allow the same session to open the same model twice" in new MockDatabaseWithModel {
        val client1 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
        val open1 = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
        val Status.Failure(cause) = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Status.Failure])
        cause shouldBe a[ModelAlreadyOpenException]
      }
    }

    "closing a closed a model" must {
      "acknowledge the close" in new MockDatabaseWithModel with OneOpenClient {
        realtimeModelActor.tell(CloseRealtimeModelRequest(domainFqn, modelId, skU1S1), client1.ref)
        val closeAck = client1.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ())
      }

      "respond with an error for an invalid cId" in new MockDatabaseWithModel with OneOpenClient {
        realtimeModelActor.tell(CloseRealtimeModelRequest(domainFqn, modelId, SessionKey(uid1, "invalidCId")), client1.ref)
         val Status.Failure(cause) = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Status.Failure])
        cause shouldBe a[ModelNotOpenException]
      }

      "notify other connected clients" in new MockDatabaseWithModel {
        val client1 = new TestProbe(system)
        val client2 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
        var client1Response = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), SessionKey(uid2, session2), client2.ref), client2.ref)
        var client2Response = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[RemoteClientOpened])

        realtimeModelActor.tell(CloseRealtimeModelRequest(domainFqn, modelId, SessionKey(uid2, session2)), client2.ref)
        val closeAck = client2.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ())

        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[RemoteClientClosed])
      }

      "notify domain when last client disconnects" in new MockDatabaseWithModel {
        val client1 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
        var client1Response = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
        realtimeModelActor.tell(CloseRealtimeModelRequest(domainFqn, modelId, skU1S1), client1.ref)
        val closeAck = client1.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ())
        mockCluster.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Passivate)
      }
    }

    "receiving an operation" must {
      "send an ack back to the submitting client" in new OneOpenClient {
        //        Mockito.when(modelOperationProcessor.processModelOperation(Matchers.any())).thenReturn(Success(()))
        //        realtimeModelActor.tell(OperationSubmission(0L, modelData.metaData.version, ObjectAddPropertyOperation("", false, "foo", NullValue(""))), client1.ref)
        //        val opAck = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OperationAcknowledgement])
      }

      "send an operation to other connected clients" in new TwoOpenClients {
        //        Mockito.when(modelOperationProcessor.processModelOperation(Matchers.any())).thenReturn(Success(()))
        //
        //        realtimeModelActor.tell(OperationSubmission(0L, modelData.metaData.version, ObjectAddPropertyOperation("", false, "foo", NullValue(""))), client1.ref)
        //        val opAck = client1.expectMsgClass(FiniteDuration(120, TimeUnit.SECONDS), classOf[OperationAcknowledgement])
        //
        //        client2.expectMsgClass(FiniteDuration(120, TimeUnit.SECONDS), classOf[OutgoingOperation])
      }

      "close a client that submits an invalid operation" in new TwoOpenClients {
        // FIXME we don't know how to stimulate this anymore because the operation
        // gets no oped because the VID doens't exists.  Maybe we need to first
        // create a VID that is an object and then target a string at it?

        //        val badOp = StringInsertOperation("", false, 1, "bad op")
        //
        //        Mockito.when(modelOperationProcessor.processModelOperation(
        //          Matchers.any())).thenReturn(Failure(new IllegalArgumentException("Induced Exception for test: Invalid Operation")))
        //
        //        realtimeModelActor.tell(OperationSubmission(
        //          0L,
        //          modelData.metaData.version,
        //          badOp), client1.ref)
        //
        //        client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[RemoteClientClosed])
        //        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ModelForceClose])
      }
    }

    "open and a model is deleted" must {
      "force close all clients" in new TwoOpenClients {
        //        realtimeModelActor.tell(ModelDeleted, modelManagerActor.ref)
        //        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ModelForceClose])
        //        client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ModelForceClose])
      }
    }

    "requested to create a model" must {
      "return ModelCreated if the model does not exist" in new TestFixture {
        //        val client = new TestProbe(system)
        //        val data = ObjectValue("", Map())
        //
        //        val now = Instant.now()
        //
        //        Mockito.when(modelStore.createModel(collectionId, Some(noModelId), data, true, modelPermissions))
        //          .thenReturn(Success(Model(ModelMetaData(collectionId, noModelId, 0L, now, now, true, modelPermissions, 1), data)))
        //
        //        Mockito.when(modelSnapshotStore.createSnapshot(any()))
        //          .thenReturn(Success(()))
        //
        //        modelManagerActor.tell(CreateModelRequest(SessionKey(userId1, sessionId1), collectionId, Some(noModelId), data, Some(true), Some(modelPermissions), None), client.ref)
        //        client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), noModelId)
      }

      "return ModelAlreadyExistsException if the model exists" in new TestFixture {
        //        val client = new TestProbe(system)
        //        val data = ObjectValue("", Map())
        //
        //        Mockito.when(modelStore.createModel(collectionId, Some(existingModelId), data, true, modelPermissions))
        //          .thenReturn(Failure(DuplicateValueException("foo")))
        //
        //        modelManagerActor.tell(CreateModelRequest(SessionKey(userId1, sessionId1), collectionId, Some(existingModelId), data, Some(true), Some(modelPermissions), None), client.ref)
        //        client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelAlreadyExistsException(existingModelId)))
      }
    }

    "requested to delete a model" must {
      "return ModelDeleted if the model exists" in new TestFixture {
        //        val client = new TestProbe(system)
        //        modelManagerActor.tell(DeleteModelRequest(SessionKey(userId1, sessionId1), existingModelId), client.ref)
        //        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ModelDeleted())
      }

      "return ModelNotFoundException if the model does not exist" in new TestFixture {
        //        val client = new TestProbe(system)
        //        modelManagerActor.tell(DeleteModelRequest(SessionKey(userId1, sessionId1), noModelId), client.ref)
        //        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelNotFoundException(noModelId)))
      }
    }

    "getting permissions" must {
      "respond with a ModelNotFoundException is the model does not exists" in new TestFixture {
        //        val client = new TestProbe(system)
        //        modelManagerActor.tell(GetModelPermissionsRequest(u1Sk, noModelId), client.ref)
        //        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelNotFoundException(noModelId)))
      }

      "respond with a UnauthorizedException if the user doesn't have read permissions" in new TestFixture {
        //        Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
        //          .thenReturn(Success(ModelPermissions(false, true, true, true)))
        //        val client = new TestProbe(system)
        //        modelManagerActor.tell(GetModelPermissionsRequest(u1Sk, existingModelId), client.ref)
        //        val Status.Failure(cause) = client.expectMsgClass(timeout, classOf[Status.Failure])
        //        cause shouldBe a[UnauthorizedException]
      }

      "respond with a GetModelPermissionsResponse if the model exists and the user has read permissions" in new TestFixture {
        //        Mockito.when(modelPermissionsResolver.getModelPermissions(any(), any())).thenReturn(
        //            Success(ModelPemrissionResult(false, ModelPermissions(true, true, true, true), Map())))
        //            
        //        val client = new TestProbe(system)
        //        modelManagerActor.tell(GetModelPermissionsRequest(u1Sk, existingModelId), client.ref)
        //        val response = client.expectMsgClass(timeout, classOf[GetModelPermissionsResponse])
      }
    }

    "setting permissions" must {
      "respond with a ModelNotFoundException is the model does not exists" in new TestFixture {
        //        val client = new TestProbe(system)
        //        val message = SetModelPermissionsRequest(u1Sk, noModelId, None, None, false, Map())
        //        modelManagerActor.tell(message, client.ref)
        //        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelNotFoundException(noModelId)))
      }

      "respond with a UnauthorizedException if the user doesn't have manage permissions" in new TestFixture {
        //        Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
        //          .thenReturn(Success(ModelPermissions(true, true, true, false)))
        //        val client = new TestProbe(system)
        //        val message = SetModelPermissionsRequest(u1Sk, existingModelId, None, None, false, Map())
        //        modelManagerActor.tell(message, client.ref)
        //        val Status.Failure(cause) = client.expectMsgClass(timeout, classOf[Status.Failure])
        //        cause shouldBe a[UnauthorizedException]
      }

      "respond with () if the model exists and the user has manage permissions" in new TestFixture {
        //        val client = new TestProbe(system)
        //        val message = SetModelPermissionsRequest(u1Sk, existingModelId, None, None, false, Map())
        //        modelManagerActor.tell(message, client.ref)
        //        val response = client.expectMsg(timeout, ())
      }
    }
  }

  trait TestFixture {
    val domainFqn = DomainFqn("convergence", "default")

    val uid1 = "u1"
    val uid2 = "u2"

    val session1 = "s1"
    val session2 = "s2"

    val skU1S1 = SessionKey(uid1, session1)

    val modelPermissions = ModelPermissions(true, true, true, true)

    val collectionId = "collection"
    val modelId = "model" + System.nanoTime()
    val modelJsonData = ObjectValue("vid1", Map("key" -> StringValue("vid2", "value")))
    val modelCreateTime = Instant.ofEpochMilli(2L)
    val modelModifiedTime = Instant.ofEpochMilli(3L)
    val modelData = Model(ModelMetaData(collectionId, modelId, 1L, modelCreateTime, modelModifiedTime, true, modelPermissions, 1), modelJsonData)
    val modelSnapshotTime = Instant.ofEpochMilli(2L)
    val modelSnapshotMetaData = ModelSnapshotMetaData(modelId, 1L, modelSnapshotTime)

    val persistenceProvider = new MockDomainPersistenceProvider()
    val persistenceManager = new MockDomainPersistenceManager(Map(domainFqn -> persistenceProvider))

    Mockito.when(persistenceProvider.collectionStore.getOrCreateCollection(collectionId)).thenReturn(Success(Collection(
      collectionId,
      collectionId,
      true,
      ModelSnapshotConfig(false, false, false, 0, 0, false, false, Duration.ofSeconds(0), Duration.ofSeconds(0)),
      CollectionPermissions(true, true, true, true, true))))
    Mockito.when(persistenceProvider.collectionStore.ensureCollectionExists(Matchers.any())).thenReturn(Success(()))

    Mockito.when(persistenceProvider.collectionStore.collectionExists(collectionId))
      .thenReturn(Success(true))

    Mockito.when(persistenceProvider.modelPermissionsStore.getCollectionWorldPermissions(collectionId)).thenReturn(Success(CollectionPermissions(true, true, true, true, true)))
    Mockito.when(persistenceProvider.modelPermissionsStore.getAllCollectionUserPermissions(collectionId)).thenReturn(Success(Map[String, CollectionPermissions]()))
    Mockito.when(persistenceProvider.modelPermissionsStore.getCollectionUserPermissions(Matchers.any(), Matchers.any())).thenReturn(Success(None))
    Mockito.when(persistenceProvider.modelPermissionsStore.updateModelUserPermissions(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Success(()))
    Mockito.when(persistenceProvider.modelPermissionsStore.updateAllModelUserPermissions(Matchers.any(), Matchers.any())).thenReturn(Success(()))
    Mockito.when(persistenceProvider.modelPermissionsStore.modelOverridesCollectionPermissions(modelId)).thenReturn(Success(false))
    Mockito.when(persistenceProvider.modelPermissionsStore.getModelWorldPermissions(modelId)).thenReturn(Success(ModelPermissions(true, true, true, true)))
    Mockito.when(persistenceProvider.modelPermissionsStore.getAllModelUserPermissions(modelId)).thenReturn(Success(Map[String, ModelPermissions]()))

    val modelPermissionsResolver = mock[ModelPermissionResolver]
    Mockito.when(modelPermissionsResolver.getModelAndCollectionPermissions(any(), any(), any()))
      .thenReturn(Success(RealTimeModelPermissions(
        false,
        CollectionPermissions(true, true, true, true, true),
        Map(),
        ModelPermissions(true, true, true, true),
        Map())))

    val modelCreator = mock[ModelCreator]
    Mockito.when(modelCreator.createModel(any(), any(), any(), any(), any(), any(), any(), any()))
      .thenReturn(Success(modelData))

    val mockCluster = TestProbe()
    val props = RealtimeModelActor.props(
      new ModelPermissionResolver(),
      modelCreator,
      persistenceManager,
      FiniteDuration(100, TimeUnit.MILLISECONDS),
      FiniteDuration(100, TimeUnit.MILLISECONDS))

    val realtimeModelActor = mockCluster.childActorOf(props)
  }

  trait MockDatabaseWithModel extends TestFixture {
    Mockito.when(persistenceProvider.modelStore.modelExists(modelId)).thenReturn(Success(true))
    Mockito.when(persistenceProvider.modelStore.getModel(modelId)).thenReturn(Success(Some(modelData)))
    Mockito.when(persistenceProvider.modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelId)).thenReturn(Success(Some(modelSnapshotMetaData)))
  }

  trait OneOpenClient extends MockDatabaseWithModel {
    val client1 = new TestProbe(system)
    realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
    val client1OpenResponse = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
  }

  trait TwoOpenClients extends OneOpenClient {
    val client2 = new TestProbe(system)
    realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), SessionKey(uid2, session1), client2.ref), client2.ref)
    val client2OpenResponse = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
    client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[RemoteClientOpened])
  }

  trait MockDatabaseWithoutModel extends TestFixture {
    Mockito.when(persistenceProvider.modelStore.modelExists(modelId)).thenReturn(Success(false))
  }
}
