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

import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Status}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.testkit.{TestKit, TestProbe}
import com.convergencelabs.convergence.server.UnknownErrorResponse
import com.convergencelabs.convergence.server.actor.ShardedActorStop
import com.convergencelabs.convergence.server.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.datastore.domain.{CollectionPermissions, ModelPermissions}
import com.convergencelabs.convergence.server.domain._
import com.convergencelabs.convergence.server.domain.model.data.{NullValue, ObjectValue, StringValue}
import com.convergencelabs.convergence.server.domain.model.ot.{ObjectAddPropertyOperation, ObjectRemovePropertyOperation}
import com.convergencelabs.convergence.server.util.{MockDomainPersistenceManager, MockDomainPersistenceProvider}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify}
import org.mockito.{Matchers, Mockito}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

// scalastyle:off magic.number
class RealtimeModelActorSpec
  extends TestKit(ActorSystem("RealtimeModelActorSpec"))
    with AnyWordSpecLike
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

        val message = client.expectMsgClass(FiniteDuration(20, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        assert(message.modelData == modelData.data)
        assert(message.metaData.version == modelData.metaData.version)
        assert(message.metaData.createdTime == modelData.metaData.createdTime)
        assert(message.metaData.modifiedTime == modelData.metaData.modifiedTime)
      }

      "notify openers of an initialization error" in new MockDatabaseWithModel {
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

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), DomainUserSessionId(session2, uid2), client2.ref), client2.ref)
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

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), DomainUserSessionId(session2, uid2), client2.ref), client2.ref)
        client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientAutoCreateModelConfigRequest])

        // Now mock that the data is there.
        Mockito.when(persistenceProvider.modelStore.createModel(modelId, collectionId, modelJsonData, true, modelPermissions))
          .thenReturn(Success(Model(ModelMetaData(collectionId, modelId, 0, now, now, true, modelPermissions, 1), modelJsonData)))
        Mockito.when(persistenceProvider.modelSnapshotStore.createSnapshot(Matchers.any())).thenReturn(Success(()))
        Mockito.when(persistenceProvider.modelStore.getModel(modelId)).thenReturn(Success(Some(modelData)))
        Mockito.when(persistenceProvider.modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelId)).thenReturn(Success(Some(modelSnapshotMetaData)))

        client1.reply(ClientAutoCreateModelConfigResponse(collectionId, Some(modelJsonData), Some(true), Some(modelPermissions), Map(), None))
        client2.reply(ClientAutoCreateModelConfigResponse(collectionId, Some(modelJsonData), Some(true), Some(modelPermissions), Map(), None))

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
        realtimeModelActor.tell(CloseRealtimeModelRequest(domainFqn, modelId, DomainUserSessionId("invalidCId", uid1)), client1.ref)
        val Status.Failure(cause) = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Status.Failure])
        cause shouldBe a[ModelNotOpenException]
      }

      "notify other connected clients" in new MockDatabaseWithModel {
        val client1 = new TestProbe(system)
        val client2 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
        var client1Response = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), DomainUserSessionId(session2, uid2), client2.ref), client2.ref)
        var client2Response = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[RemoteClientOpened])

        realtimeModelActor.tell(CloseRealtimeModelRequest(domainFqn, modelId, DomainUserSessionId(session2, uid2)), client2.ref)
        val closeAck = client2.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ())

        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[RemoteClientClosed])
      }

      "notify domain when last client disconnects" in new MockDatabaseWithModel {
        val client1 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
        var client1Response = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
        realtimeModelActor.tell(CloseRealtimeModelRequest(domainFqn, modelId, skU1S1), client1.ref)
        val closeAck = client1.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ())
        mockCluster.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Passivate(ShardedActorStop))
      }
    }

    "receiving an operation" must {
      "send an ack back to the submitting client" in new OneOpenClient {
        Mockito.when(persistenceProvider.modelOperationProcessor.processModelOperation(Matchers.any())).thenReturn(Success(()))
        realtimeModelActor.tell(OperationSubmission(domainFqn, modelId, 0, modelData.metaData.version, ObjectAddPropertyOperation("", false, "foo", NullValue(""))), client1.ref)
        val opAck = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OperationAcknowledgement])
      }

      "send an operation to other connected clients" in new TwoOpenClients {
        Mockito.when(persistenceProvider.modelOperationProcessor.processModelOperation(Matchers.any())).thenReturn(Success(()))
        realtimeModelActor.tell(OperationSubmission(domainFqn, modelId, 0, modelData.metaData.version, ObjectAddPropertyOperation("", false, "foo", NullValue(""))), client1.ref)
        val opAck = client1.expectMsgClass(FiniteDuration(120, TimeUnit.SECONDS), classOf[OperationAcknowledgement])

        client2.expectMsgClass(FiniteDuration(120, TimeUnit.SECONDS), classOf[OutgoingOperation])
      }

      "close a client that submits an invalid operation" in new TwoOpenClients {
        val badOp = ObjectRemovePropertyOperation(modelJsonData.id, false, "not real")
        Mockito.when(persistenceProvider.modelOperationProcessor.processModelOperation(
          Matchers.any())).thenReturn(Failure(new IllegalArgumentException("Induced Exception for test: Invalid Operation")))

        realtimeModelActor.tell(OperationSubmission(domainFqn, modelId,
          0,
          modelData.metaData.version,
          badOp), client1.ref)

        client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[RemoteClientClosed])
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ModelForceClose])
      }

      "close a client that submits an invalid version" in new TwoOpenClients {
        val badOp = ObjectRemovePropertyOperation(modelJsonData.id, false, "not real")
        realtimeModelActor.tell(OperationSubmission(domainFqn, modelId, 100, modelData.metaData.version, badOp), client1.ref)

        client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[RemoteClientClosed])
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ModelForceClose])
      }
    }

    "open and a model is deleted" must {
      "force close all clients" in new TwoOpenClients {
        val message = DeleteRealtimeModel(domainFqn, modelId, None)
        realtimeModelActor.tell(message, ActorRef.noSender)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ModelForceClose])
        client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ModelForceClose])
      }
    }

    "requested to create a model" must {
      "return ModelCreated if the model does not exist" in new TestFixture {
        val client = new TestProbe(system)
        val data = ObjectValue("", Map())

        Mockito.when(modelCreator.createModel(
          any(),
          any(),
          Matchers.eq(collectionId),
          Matchers.eq(noModelId),
          Matchers.eq(data),
          any(),
          any(),
          any()))
          .thenReturn(Success(Model(ModelMetaData(noModelId, collectionId, 0, now, now, true, modelPermissions, 1), data)))

        Mockito.when(persistenceProvider.modelSnapshotStore.createSnapshot(any())).thenReturn(Success(()))
        realtimeModelActor.tell(CreateRealtimeModel(domainFqn, noModelId, collectionId, data, Some(true), Some(modelPermissions), Map(), Some(skU1S1)), client.ref)
        client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), noModelId)
      }

      "return ModelAlreadyExistsException if the model exists" in new MockDatabaseWithModel {
        val client = new TestProbe(system)
        val data = ObjectValue("", Map())

        realtimeModelActor.tell(CreateRealtimeModel(domainFqn, modelId, collectionId, data, Some(true), Some(modelPermissions), Map(), Some(skU1S1)), client.ref)
        client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelAlreadyExistsException(modelId)))
      }
    }

    "requested to delete a model" must {
      "return Success if the model exists" in new MockDatabaseWithModel {
        val client = new TestProbe(system)
        realtimeModelActor.tell(DeleteRealtimeModel(domainFqn, modelId, None), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ())
      }

      "return ModelNotFoundException if the model does not exist" in new TestFixture {
        val client = new TestProbe(system)
        realtimeModelActor.tell(DeleteRealtimeModel(domainFqn, noModelId, Some(skU1S1)), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelNotFoundException(noModelId)))
      }
    }

    "getting permissions" must {
      "respond with a ModelNotFoundException is the model does not exists" in new TestFixture {
        val client = new TestProbe(system)
        realtimeModelActor.tell(GetModelPermissionsRequest(domainFqn, noModelId, skU1S1), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelNotFoundException(noModelId)))
      }

      "respond with a UnauthorizedException if the user doesn't have read permissions" in new MockDatabaseWithModel {
        Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
          .thenReturn(Success(ModelPermissions(false, true, true, true)))
        val client = new TestProbe(system)
        realtimeModelActor.tell(GetModelPermissionsRequest(domainFqn, modelId, skU1S1), client.ref)
        val Status.Failure(cause) = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Status.Failure])
        cause shouldBe a[UnauthorizedException]
      }

      "respond with a GetModelPermissionsResponse if the model exists and the user has read permissions" in new MockDatabaseWithModel {
        Mockito.when(modelPermissionsResolver.getModelPermissions(any(), any())).thenReturn(
          Success(ModelPermissionResult(false, ModelPermissions(true, true, true, true), Map())))
        Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
          .thenReturn(Success(ModelPermissions(true, true, true, true)))
        val client = new TestProbe(system)
        realtimeModelActor.tell(GetModelPermissionsRequest(domainFqn, modelId, skU1S1), client.ref)
        val response = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[GetModelPermissionsResponse])
      }
    }

    "setting permissions" must {
      "respond with a ModelNotFoundException is the model does not exists" in new TestFixture {
        val client = new TestProbe(system)
        val message = SetModelPermissionsRequest(domainFqn, noModelId, skU1S1, None, None, false, Map(), List())
        realtimeModelActor.tell(message, client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), Status.Failure(ModelNotFoundException(noModelId)))
      }

      "respond with a UnauthorizedException if the user doesn't have manage permissions" in new MockDatabaseWithModel {
        Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
          .thenReturn(Success(ModelPermissions(true, true, true, false)))
        val client = new TestProbe(system)
        val message = SetModelPermissionsRequest(domainFqn, modelId, skU1S1, None, None, false, Map(), List())
        realtimeModelActor.tell(message, client.ref)
        val Status.Failure(cause) = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[Status.Failure])
        cause shouldBe a[UnauthorizedException]
      }

      "respond with () if the model exists and the user has manage permissions" in new MockDatabaseWithModel {
        val client = new TestProbe(system)
        Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
          .thenReturn(Success(ModelPermissions(true, true, true, true)))
        val message = SetModelPermissionsRequest(domainFqn, modelId, skU1S1, None, None, false, Map(), List())
        realtimeModelActor.tell(message, client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ())
      }
    }
  }

  trait TestFixture {
    val domainFqn = DomainId("convergence", "default")

    val uid1 = DomainUserId.normal("u1")
    val uid2 = DomainUserId.normal("u2")

    val session1 = "s1"
    val session2 = "s2"

    val now = Instant.now()

    val skU1S1 = DomainUserSessionId(session1, uid1)

    val modelPermissions = ModelPermissions(true, true, true, true)

    val collectionId = "collection"
    val modelId = "model" + System.nanoTime()
    val modelJsonData = ObjectValue("vid1", Map("key" -> StringValue("vid2", "value")))
    val modelCreateTime = Instant.ofEpochMilli(2L)
    val modelModifiedTime = Instant.ofEpochMilli(3L)
    val modelData = Model(ModelMetaData(modelId, collectionId, 1, modelCreateTime, modelModifiedTime, true, modelPermissions, 1), modelJsonData)
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

    Mockito.when(persistenceProvider.collectionStore.collectionExists(collectionId)).thenReturn(Success(true))

    Mockito.when(persistenceProvider.modelPermissionsStore.getCollectionWorldPermissions(collectionId)).thenReturn(Success(CollectionPermissions(true, true, true, true, true)))
    Mockito.when(persistenceProvider.modelPermissionsStore.getAllCollectionUserPermissions(collectionId)).thenReturn(Success(Map[DomainUserId, CollectionPermissions]()))
    Mockito.when(persistenceProvider.modelPermissionsStore.getCollectionUserPermissions(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Success(None))
    Mockito.when(persistenceProvider.modelPermissionsStore.updateModelUserPermissions(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Success(()))
    Mockito.when(persistenceProvider.modelPermissionsStore.updateAllModelUserPermissions(Matchers.any(), Matchers.any())).thenReturn(Success(()))
    Mockito.when(persistenceProvider.modelPermissionsStore.modelOverridesCollectionPermissions(modelId)).thenReturn(Success(false))
    Mockito.when(persistenceProvider.modelPermissionsStore.getModelWorldPermissions(modelId)).thenReturn(Success(ModelPermissions(true, true, true, true)))
    Mockito.when(persistenceProvider.modelPermissionsStore.getAllModelUserPermissions(modelId)).thenReturn(Success(Map[DomainUserId, ModelPermissions]()))

    val modelPermissionsResolver = mock[ModelPermissionResolver]
    Mockito.when(modelPermissionsResolver.getModelAndCollectionPermissions(any(), any(), any()))
      .thenReturn(Success(RealTimeModelPermissions(
        false,
        CollectionPermissions(true, true, true, true, true),
        Map(),
        ModelPermissions(true, true, true, true),
        Map())))

    val noModelId = "non existent model"
    Mockito.when(persistenceProvider.modelStore.modelExists(noModelId)).thenReturn(Success(false))
    Mockito.when(persistenceProvider.modelStore.getAndIncrementNextValuePrefix(any())).thenReturn(Success(1L))


    val modelCreator = mock[ModelCreator]

    val mockCluster = TestProbe()
    val props = RealtimeModelActor.props(
      modelPermissionsResolver,
      modelCreator,
      persistenceManager,
      FiniteDuration(100, TimeUnit.MILLISECONDS),
      FiniteDuration(100, TimeUnit.MILLISECONDS),
      FiniteDuration(100, TimeUnit.MILLISECONDS))

    val realtimeModelActor = mockCluster.childActorOf(props)
  }

  trait MockDatabaseWithModel extends TestFixture {
    Mockito.when(persistenceProvider.modelStore.modelExists(modelId)).thenReturn(Success(true))
    Mockito.when(modelCreator.createModel(any(), any(), Matchers.eq(collectionId), Matchers.eq(modelId), any(), any(), any(), any()))
      .thenReturn(Failure(DuplicateValueException("modelId")))

    Mockito.when(persistenceProvider.modelStore.deleteModel(modelId)).thenReturn(Success(()))

    Mockito.when(persistenceProvider.modelStore.getModel(modelId)).thenReturn(Success(Some(modelData)))
    Mockito.when(persistenceProvider.modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelId)).thenReturn(Success(Some(modelSnapshotMetaData)))
  }

  trait OneOpenClient extends MockDatabaseWithModel {
    val client1 = new TestProbe(system)
    realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref), client1.ref)
    val client1OpenResponse: OpenModelSuccess = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
  }

  trait TwoOpenClients extends OneOpenClient {
    val client2 = new TestProbe(system)
    realtimeModelActor.tell(OpenRealtimeModelRequest(domainFqn, modelId, Some(1), DomainUserSessionId(session2, uid2), client2.ref), client2.ref)
    val client2OpenResponse: OpenModelSuccess = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
    client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[RemoteClientOpened])
  }

  trait MockDatabaseWithoutModel extends TestFixture {
    // Now mock that the data is there.
    Mockito.when(modelCreator.createModel(
      any(),
      any(),
      Matchers.eq(collectionId),
      Matchers.eq(modelId),
      Matchers.eq(modelJsonData),
      any(),
      any(),
      any()))
      .thenReturn(Success(Model(ModelMetaData(collectionId, noModelId, 0, now, now, true, modelPermissions, 1), modelJsonData)))

    Mockito.when(persistenceProvider.modelStore.modelExists(modelId)).thenReturn(Success(false))
  }

}
