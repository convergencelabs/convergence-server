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

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.ClusterSharding.Passivate
import com.convergencelabs.convergence.server.InducedTestingException
import com.convergencelabs.convergence.server.api.realtime
import com.convergencelabs.convergence.server.api.realtime.ModelClientActor
import com.convergencelabs.convergence.server.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.datastore.domain.{CollectionPermissions, ModelPermissions}
import com.convergencelabs.convergence.server.domain._
import com.convergencelabs.convergence.server.domain.model.RealtimeModelActor.{ModelAlreadyExistsError, UnknownError}
import com.convergencelabs.convergence.server.domain.model.data.{NullValue, ObjectValue, StringValue}
import com.convergencelabs.convergence.server.domain.model.ot.{ObjectAddPropertyOperation, ObjectRemovePropertyOperation}
import com.convergencelabs.convergence.server.util.{MockDomainPersistenceManager, MockDomainPersistenceProvider}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify}
import org.mockito.{Matchers, Mockito}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

// scalastyle:off magic.number
class RealtimeModelActorSpec
  extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A RealtimeModelActor" when {
    "opening a closed model" must {
      "load the model from the database if it is persisted" in new MockDatabaseWithModel {
        {
          val replyTo = testKit.createTestProbe[RealtimeModelActor.OpenRealtimeModelResponse]()
          val client = testKit.createTestProbe[ModelClientActor.OutgoingMessage]()
          realtimeModelActor !
            RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client.ref, replyTo.ref)

          val response: RealtimeModelActor.OpenRealtimeModelResponse =
            replyTo.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(20, TimeUnit.SECONDS))

          assert(response.response.isRight)
          response.response.foreach { message =>
            assert(message.modelData == modelData.data)
            assert(message.metaData.version == modelData.metaData.version)
            assert(message.metaData.createdTime == modelData.metaData.createdTime)
            assert(message.metaData.modifiedTime == modelData.metaData.modifiedTime)
          }
        }
      }

      "notify openers of an initialization error" in new MockDatabaseWithModel {
        {
          val replyTo = testKit.createTestProbe[RealtimeModelActor.OpenRealtimeModelResponse]()
          val client = testKit.createTestProbe[ModelClientActor.OutgoingMessage]()

          // Set the database up to bomb
          Mockito.when(persistenceProvider.modelStore.getModel(Matchers.any())).thenThrow(InducedTestingException())

          realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client.ref, replyTo.ref)
          val message: RealtimeModelActor.OpenRealtimeModelResponse =
            replyTo.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
          assert(message.response.isLeft)
          message.response.left.map { err =>
            err shouldBe UnknownError()
          }
        }
      }

      "ask all connecting clients for state if it is not persisted" in new MockDatabaseWithoutModel {
        {
          val replyTo1 = testKit.createTestProbe[RealtimeModelActor.OpenRealtimeModelResponse]()
          val client1 = testKit.createTestProbe[ModelClientActor.OutgoingMessage]()

          val replyTo2 = testKit.createTestProbe[RealtimeModelActor.OpenRealtimeModelResponse]()
          val client2 = testKit.createTestProbe[ModelClientActor.OutgoingMessage]()

          realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref, replyTo1.ref)
          val dataRequest1: ModelClientActor.ClientAutoCreateModelConfigRequest =
            client1.expectMessageType[ModelClientActor.ClientAutoCreateModelConfigRequest](FiniteDuration(1, TimeUnit.SECONDS))
          assert(dataRequest1.autoConfigId == 1)

          realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), DomainUserSessionId(session2, uid2), client2.ref, replyTo2.ref)
          val dataRequest2: ModelClientActor.ClientAutoCreateModelConfigRequest =
            client2.expectMessageType[ModelClientActor.ClientAutoCreateModelConfigRequest](FiniteDuration(1, TimeUnit.SECONDS))
          assert(dataRequest2.autoConfigId == 1)
        }
      }

      "reject a client that does not respond with data" in new MockDatabaseWithoutModel {
        {
          val replyTo1 = testKit.createTestProbe[RealtimeModelActor.OpenRealtimeModelResponse]()
          val client1 = testKit.createTestProbe[ModelClientActor.OutgoingMessage]()

          realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref, replyTo1.ref)
          val _: ModelClientActor.ClientAutoCreateModelConfigRequest =
            client1.expectMessageType[ModelClientActor.ClientAutoCreateModelConfigRequest](FiniteDuration(1, TimeUnit.SECONDS))

          val message: RealtimeModelActor.OpenRealtimeModelResponse =
            replyTo1.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(200, TimeUnit.MILLISECONDS))
          assert(message.response.isLeft)
          message.response.left.map { err =>
            err shouldBe a[RealtimeModelActor.ClientErrorResponse]
          }
        }
      }

      "notify all queued clients when data is returned by the first client" in new MockDatabaseWithoutModel {
        {
          val replyTo1 = testKit.createTestProbe[RealtimeModelActor.OpenRealtimeModelResponse]()
          val client1 = testKit.createTestProbe[ModelClientActor.OutgoingMessage]()

          val replyTo2 = testKit.createTestProbe[RealtimeModelActor.OpenRealtimeModelResponse]()
          val client2 = testKit.createTestProbe[ModelClientActor.OutgoingMessage]()

          realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref, replyTo1.ref)
          val req1 = client1.expectMessageType[ModelClientActor.ClientAutoCreateModelConfigRequest](FiniteDuration(1, TimeUnit.SECONDS))

          realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), DomainUserSessionId(session2, uid2), client2.ref, replyTo2.ref)
          val req2 = client2.expectMessageType[ModelClientActor.ClientAutoCreateModelConfigRequest](FiniteDuration(1, TimeUnit.SECONDS))

          // Now mock that the data is there.
          Mockito.when(persistenceProvider.modelStore.createModel(modelId, collectionId, modelJsonData, overridePermissions = true, modelPermissions))
            .thenReturn(Success(Model(ModelMetaData(collectionId, modelId, 0, now, now, overridePermissions = true, modelPermissions, 1), modelJsonData)))
          Mockito.when(persistenceProvider.modelSnapshotStore.createSnapshot(Matchers.any())).thenReturn(Success(()))
          Mockito.when(persistenceProvider.modelStore.getModel(modelId)).thenReturn(Success(Some(modelData)))
          Mockito.when(persistenceProvider.modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelId)).thenReturn(Success(Some(modelSnapshotMetaData)))

          val config = ModelClientActor.ClientAutoCreateModelConfig(collectionId, Some(modelJsonData), Some(true), Some(modelPermissions), Map(), None)
          req1.replyTo ! ModelClientActor.ClientAutoCreateModelConfigResponse(Right(config))
          req2.replyTo ! ModelClientActor.ClientAutoCreateModelConfigResponse(Right(config))

          // Verify that both clients got the data.
          val openResponse1: RealtimeModelActor.OpenRealtimeModelResponse =
            replyTo1.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
          val openResponse2: RealtimeModelActor.OpenRealtimeModelResponse =
            replyTo2.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))

          assert(openResponse1.response.isRight)
          openResponse1.response.map { r =>
            assert(r.modelData == modelJsonData)
            assert(r.metaData.version == modelData.metaData.version)
            assert(r.metaData.createdTime == modelData.metaData.createdTime)
            assert(r.metaData.modifiedTime == modelData.metaData.modifiedTime)
          }

          assert(openResponse2.response.isRight)
          openResponse2.response.map { r =>
            assert(r.modelData == modelJsonData)
            assert(r.metaData.version == modelData.metaData.version)
            assert(r.metaData.createdTime == modelData.metaData.createdTime)
            assert(r.metaData.modifiedTime == modelData.metaData.modifiedTime)
          }

          // Verify that the model and snapshot were created.
          // FIXME use ary capture to match it.
          verify(modelCreator, times(1)).createModel(any(), any(), any(), any(), any(), any(), any(), any())
        }
      }
    }

    "opening an open model" must {
      "not allow the same session to open the same model twice" in new MockDatabaseWithModel {
        val client1: TestProbe[ModelClientActor.OutgoingMessage] = testKit.createTestProbe()
        val replyTo1: TestProbe[RealtimeModelActor.OpenRealtimeModelResponse] = testKit.createTestProbe()

        realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref, replyTo1.ref)
        val firstOpen: RealtimeModelActor.OpenRealtimeModelResponse =
          replyTo1.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(30, TimeUnit.SECONDS))
        assert(firstOpen.response.isRight)

        realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref, replyTo1.ref)
        val secondOpen: RealtimeModelActor.OpenRealtimeModelResponse =
          replyTo1.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
        assert(secondOpen.response.isLeft)
        secondOpen.response.map(err => err shouldBe RealtimeModelActor.ModelOpenError)
      }
    }

    "closing a closed a model" must {
      "acknowledge the close" in new MockDatabaseWithModel with OneOpenClient {
        val closeReply: TestProbe[RealtimeModelActor.CloseRealtimeModelResponse] = testKit.createTestProbe()
        realtimeModelActor ! RealtimeModelActor.CloseRealtimeModelRequest(domainFqn, modelId, skU1S1, closeReply.ref)
        val closeAck: RealtimeModelActor.CloseRealtimeModelResponse =
          closeReply.expectMessageType[RealtimeModelActor.CloseRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
        assert(closeAck.response.isRight)
      }

      "respond with an error for an invalid cId" in new MockDatabaseWithModel with OneOpenClient {
        val closeReply: TestProbe[RealtimeModelActor.CloseRealtimeModelResponse] = testKit.createTestProbe()
        realtimeModelActor ! RealtimeModelActor.CloseRealtimeModelRequest(domainFqn, modelId, DomainUserSessionId("invalidCId", uid1), closeReply.ref)
        val response: RealtimeModelActor.CloseRealtimeModelResponse =
          closeReply.expectMessageType[RealtimeModelActor.CloseRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
        assert(response.response.isLeft)
        response.response.left.map(err => err shouldBe RealtimeModelActor.ModelNotOpenError())
      }

      "notify other connected clients" in new MockDatabaseWithModel {
        {
          val replyTo1 = testKit.createTestProbe[RealtimeModelActor.OpenRealtimeModelResponse]()
          val client1 = testKit.createTestProbe[ModelClientActor.OutgoingMessage]()

          val replyTo2 = testKit.createTestProbe[RealtimeModelActor.OpenRealtimeModelResponse]()
          val client2 = testKit.createTestProbe[ModelClientActor.OutgoingMessage]()

          realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref, replyTo1.ref)
          val client1Response: RealtimeModelActor.OpenRealtimeModelResponse =
            replyTo1.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
          assert(client1Response.response.isRight)

          val s2 = DomainUserSessionId(session2, uid2)
          realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(
            domainFqn, modelId, Some(1), s2, client2.ref, replyTo2.ref)
          val client2Response: RealtimeModelActor.OpenRealtimeModelResponse =
            replyTo2.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
          assert(client2Response.response.isRight)

          val clientOpenedMessage: realtime.ModelClientActor.RemoteClientOpened =
            client1.expectMessageType[realtime.ModelClientActor.RemoteClientOpened](FiniteDuration(1, TimeUnit.SECONDS))
          clientOpenedMessage.session shouldBe s2

          val closeReply = testKit.createTestProbe[RealtimeModelActor.CloseRealtimeModelResponse]()
          realtimeModelActor ! RealtimeModelActor.CloseRealtimeModelRequest(domainFqn, modelId, s2, closeReply.ref)
          val closeAck: RealtimeModelActor.CloseRealtimeModelResponse =
            closeReply.expectMessageType[RealtimeModelActor.CloseRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
          assert(closeAck.response.isRight)

          val clientCloseMessage: ModelClientActor.RemoteClientClosed =
            client1.expectMessageType[ModelClientActor.RemoteClientClosed](FiniteDuration(1, TimeUnit.SECONDS))
          clientCloseMessage.session shouldBe s2
        }
      }

      "notify domain when last client disconnects" in new MockDatabaseWithModel {
        {
          val replyTo1 = testKit.createTestProbe[RealtimeModelActor.OpenRealtimeModelResponse]()
          val client1 = testKit.createTestProbe[ModelClientActor.OutgoingMessage]()

          realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref, replyTo1.ref)
          val client1Response: RealtimeModelActor.OpenRealtimeModelResponse =
            replyTo1.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
          assert(client1Response.response.isRight)

          val closeReply = testKit.createTestProbe[RealtimeModelActor.CloseRealtimeModelResponse]()
          realtimeModelActor ! RealtimeModelActor.CloseRealtimeModelRequest(domainFqn, modelId, skU1S1, closeReply.ref)
          val closeAck: RealtimeModelActor.CloseRealtimeModelResponse =
            closeReply.expectMessageType[RealtimeModelActor.CloseRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
          assert(closeAck.response.isRight)
          val p: Passivate[RealtimeModelActor.Message] =
            shard.expectMessageType[Passivate[RealtimeModelActor.Message]](FiniteDuration(1, TimeUnit.SECONDS))
          p shouldBe Passivate(realtimeModelActor)
        }
      }
    }

    "receiving an operation" must {
      "send an ack back to the submitting client" in new OneOpenClient {
        Mockito.when(persistenceProvider.modelOperationProcessor.processModelOperation(Matchers.any())).thenReturn(Success(()))
        realtimeModelActor ! RealtimeModelActor.OperationSubmission(
          domainFqn, modelId, skU1S1, 0, modelData.metaData.version, ObjectAddPropertyOperation("", noOp = false, "foo", NullValue("")))
        val opAck: ModelClientActor.OperationAcknowledgement =
          client1.expectMessageType[ModelClientActor.OperationAcknowledgement](FiniteDuration(1, TimeUnit.SECONDS))
        opAck.modelId shouldBe modelId
        opAck.seqNo shouldBe 0
      }

      "send an operation to other connected clients" in new TwoOpenClients {
        Mockito.when(persistenceProvider.modelOperationProcessor.processModelOperation(Matchers.any())).thenReturn(Success(()))
        realtimeModelActor ! RealtimeModelActor.OperationSubmission(
          domainFqn, modelId, skU1S1, 0, modelData.metaData.version, ObjectAddPropertyOperation("", noOp = false, "foo", NullValue("")))
        client1.expectMessageType[ModelClientActor.OperationAcknowledgement](FiniteDuration(120, TimeUnit.SECONDS))
        client2.expectMessageType[ModelClientActor.OutgoingOperation](FiniteDuration(120, TimeUnit.SECONDS))
      }

      "close a client that submits an invalid operation" in new TwoOpenClients {
        {
          val badOp = ObjectRemovePropertyOperation(modelJsonData.id, noOp = false, "not real")
          Mockito.when(persistenceProvider.modelOperationProcessor.processModelOperation(
            Matchers.any())).thenReturn(Failure(InducedTestingException()))

          realtimeModelActor ! RealtimeModelActor.OperationSubmission(domainFqn, modelId, skU1S1, 0, modelData.metaData.version, badOp)

          client2.expectMessageType[ModelClientActor.RemoteClientClosed](FiniteDuration(1, TimeUnit.SECONDS))
          client1.expectMessageType[ModelClientActor.ModelForceClose](FiniteDuration(1, TimeUnit.SECONDS))
        }
      }

      "close a client that submits an invalid version" in new TwoOpenClients {
        {
          val badOp = ObjectRemovePropertyOperation(modelJsonData.id, noOp = false, "not real")
          realtimeModelActor ! RealtimeModelActor.OperationSubmission(
            domainFqn, modelId, skU1S1, 100, modelData.metaData.version, badOp)

          client2.expectMessageType[ModelClientActor.RemoteClientClosed](FiniteDuration(1, TimeUnit.SECONDS))
          client1.expectMessageType[ModelClientActor.ModelForceClose](FiniteDuration(1, TimeUnit.SECONDS))
        }
      }
    }

    "open and a model is deleted" must {
      "force close all clients" in new TwoOpenClients {
        {
          val deleteReplyTo: TestProbe[RealtimeModelActor.DeleteRealtimeModelResponse] = testKit.createTestProbe()
          val message = RealtimeModelActor.DeleteRealtimeModelRequest(domainFqn, modelId, None, deleteReplyTo.ref)
          realtimeModelActor ! message
          client1.expectMessageType[ModelClientActor.ModelForceClose](FiniteDuration(1, TimeUnit.SECONDS))
          client2.expectMessageType[ModelClientActor.ModelForceClose](FiniteDuration(1, TimeUnit.SECONDS))
        }
      }
    }

    "requested to create a model" must {
      "return ModelCreated if the model does not exist" in new TestFixture {
        {
          val client = testKit.createTestProbe()
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
            .thenReturn(Success(Model(ModelMetaData(noModelId, collectionId, 0, now, now, overridePermissions = true, modelPermissions, 1), data)))

          Mockito.when(persistenceProvider.modelSnapshotStore.createSnapshot(any())).thenReturn(Success(()))
          val createReplyTo: TestProbe[RealtimeModelActor.CreateRealtimeModelResponse] = testKit.createTestProbe()
          realtimeModelActor ! RealtimeModelActor.CreateRealtimeModelRequest(domainFqn, noModelId, collectionId, data, Some(true), Some(modelPermissions), Map(), Some(skU1S1), createReplyTo.ref)
          val resp: RealtimeModelActor.CreateRealtimeModelResponse =
            createReplyTo.expectMessageType[RealtimeModelActor.CreateRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
          assert(resp.response.isRight)
          resp.response.map(modelId => modelId shouldBe noModelId)
        }
      }

      "return ModelAlreadyExistsException if the model exists" in new MockDatabaseWithModel {
        {
          val client: TestProbe[RealtimeModelActor.CreateRealtimeModelResponse] = testKit.createTestProbe()
          val data = ObjectValue("", Map())

          realtimeModelActor ! RealtimeModelActor.CreateRealtimeModelRequest(
            domainFqn, modelId, collectionId, data, Some(true), Some(modelPermissions), Map(), Some(skU1S1), client.ref)
          val msg = client.expectMessageType[RealtimeModelActor.CreateRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
          assert(msg.response.isLeft)
          msg.response.left.map(err => err shouldBe ModelAlreadyExistsError())
        }
      }
    }

    "requested to delete a model" must {
      "return Success if the model exists" in new MockDatabaseWithModel {
        val client: TestProbe[RealtimeModelActor.DeleteRealtimeModelResponse] = testKit.createTestProbe()
        realtimeModelActor ! RealtimeModelActor.DeleteRealtimeModelRequest(domainFqn, modelId, None, client.ref)
        val msg: RealtimeModelActor.DeleteRealtimeModelResponse =
          client.expectMessageType[RealtimeModelActor.DeleteRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
        assert(msg.response.isRight)
      }

      "return ModelNotFoundException if the model does not exist" in new TestFixture {
        val client: TestProbe[RealtimeModelActor.DeleteRealtimeModelResponse] = testKit.createTestProbe()
        realtimeModelActor ! RealtimeModelActor.DeleteRealtimeModelRequest(domainFqn, noModelId, Some(skU1S1), client.ref)
        val msg: RealtimeModelActor.DeleteRealtimeModelResponse =
          client.expectMessageType[RealtimeModelActor.DeleteRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
        assert(msg.response.isLeft)
        msg.response.left.map(err => err shouldBe RealtimeModelActor.ModelNotFoundError())
      }
    }

    "getting permissions" must {
      "respond with a ModelNotFoundException is the model does not exists" in new TestFixture {
        val client: TestProbe[RealtimeModelActor.GetModelPermissionsResponse] = testKit.createTestProbe()
        realtimeModelActor ! RealtimeModelActor.GetModelPermissionsRequest(domainFqn, noModelId, skU1S1, client.ref)
        val resp: RealtimeModelActor.GetModelPermissionsResponse =
          client.expectMessageType[RealtimeModelActor.GetModelPermissionsResponse](FiniteDuration(1, TimeUnit.SECONDS))
        resp.response.left.map { err =>
          err shouldBe RealtimeModelActor.ModelNotFoundError()
        }
      }

      "respond with a UnauthorizedException if the user doesn't have read permissions" in new MockDatabaseWithModel {
        Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
          .thenReturn(Success(ModelPermissions(read = false, write = true, remove = true, manage = true)))
        val client: TestProbe[RealtimeModelActor.GetModelPermissionsResponse] = testKit.createTestProbe()
        realtimeModelActor ! RealtimeModelActor.GetModelPermissionsRequest(domainFqn, modelId, skU1S1, client.ref)
        val resp: RealtimeModelActor.GetModelPermissionsResponse =
          client.expectMessageType[RealtimeModelActor.GetModelPermissionsResponse](FiniteDuration(1, TimeUnit.SECONDS))
        assert(resp.response.isLeft)
        resp.response.left.map { err =>
          err shouldBe a[RealtimeModelActor.UnauthorizedError]
        }
      }

      "respond with a GetModelPermissionsResponse if the model exists and the user has read permissions" in new MockDatabaseWithModel {
        Mockito.when(modelPermissionsResolver.getModelPermissions(any(), any())).thenReturn(
          Success(ModelPermissionResult(overrideCollection = false, ModelPermissions(read = true, write = true, remove = true, manage = true), Map())))
        Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
          .thenReturn(Success(ModelPermissions(read = true, write = true, remove = true, manage = true)))
        val client: TestProbe[RealtimeModelActor.GetModelPermissionsResponse] = testKit.createTestProbe()
        realtimeModelActor ! RealtimeModelActor.GetModelPermissionsRequest(domainFqn, modelId, skU1S1, client.ref)
        val response: RealtimeModelActor.GetModelPermissionsResponse =
          client.expectMessageType[RealtimeModelActor.GetModelPermissionsResponse](FiniteDuration(1, TimeUnit.SECONDS))
        assert(response.response.isRight)
      }
    }

    "setting permissions" must {
      "respond with a ModelNotFoundException is the model does not exists" in new TestFixture {
        {
          val client: TestProbe[RealtimeModelActor.SetModelPermissionsResponse] = testKit.createTestProbe()
          val message = RealtimeModelActor.SetModelPermissionsRequest(domainFqn, noModelId, skU1S1, None, None, setAllUserPermissions = false, Map(), List(), client.ref)
          realtimeModelActor ! message
          val resp: RealtimeModelActor.SetModelPermissionsResponse =
            client.expectMessageType[RealtimeModelActor.SetModelPermissionsResponse](FiniteDuration(1, TimeUnit.SECONDS))
          assert(resp.response.isLeft)
          resp.response.left.map { err =>
            err shouldBe RealtimeModelActor.ModelNotFoundError()
          }
        }
      }

      "respond with a UnauthorizedException if the user doesn't have manage permissions" in new MockDatabaseWithModel {
        {
          Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
            .thenReturn(Success(ModelPermissions(read = true, write = true, remove = true, manage = false)))
          val client: TestProbe[RealtimeModelActor.SetModelPermissionsResponse] = testKit.createTestProbe()
          val message = RealtimeModelActor.SetModelPermissionsRequest(domainFqn, modelId, skU1S1, None, None, setAllUserPermissions = false, Map(), List(), client.ref)
          realtimeModelActor ! message
          val resp: RealtimeModelActor.SetModelPermissionsResponse =
            client.expectMessageType[RealtimeModelActor.SetModelPermissionsResponse](FiniteDuration(1, TimeUnit.SECONDS))
          assert(resp.response.isLeft)
          resp.response.left.map { err =>
            err shouldBe a[RealtimeModelActor.UnauthorizedError]
          }
        }
      }

      "respond with () if the model exists and the user has manage permissions" in new MockDatabaseWithModel {
        {
          val client: TestProbe[RealtimeModelActor.SetModelPermissionsResponse] = testKit.createTestProbe()
          Mockito.when(modelPermissionsResolver.getModelUserPermissions(any(), any(), any()))
            .thenReturn(Success(ModelPermissions(read = true, write = true, remove = true, manage = true)))
          val message = RealtimeModelActor.SetModelPermissionsRequest(domainFqn, modelId, skU1S1, None, None, setAllUserPermissions = false, Map(), List(), client.ref)

          realtimeModelActor ! message
          val response: RealtimeModelActor.SetModelPermissionsResponse =
            client.expectMessageType[RealtimeModelActor.SetModelPermissionsResponse](FiniteDuration(1, TimeUnit.SECONDS))
          assert(response.response.isRight)
        }
      }
    }
  }

  trait TestFixture {
    val domainFqn: DomainId = DomainId("convergence", "default")

    val uid1: DomainUserId = DomainUserId.normal("u1")
    val uid2: DomainUserId = DomainUserId.normal("u2")

    val session1 = "s1"
    val session2 = "s2"

    val now: Instant = Instant.now()

    val skU1S1: DomainUserSessionId = DomainUserSessionId(session1, uid1)

    val modelPermissions: ModelPermissions = ModelPermissions(read = true, write = true, remove = true, manage = true)

    val collectionId = "collection"
    val modelId: String = "model" + System.nanoTime()
    val modelJsonData: ObjectValue = ObjectValue("vid1", Map("key" -> StringValue("vid2", "value")))
    val modelCreateTime: Instant = Instant.ofEpochMilli(2L)
    val modelModifiedTime: Instant = Instant.ofEpochMilli(3L)
    val modelData: Model = Model(ModelMetaData(
      modelId, collectionId, 1, modelCreateTime, modelModifiedTime, overridePermissions = true, modelPermissions, 1), modelJsonData)
    val modelSnapshotTime: Instant = Instant.ofEpochMilli(2L)
    val modelSnapshotMetaData: ModelSnapshotMetaData = ModelSnapshotMetaData(modelId, 1L, modelSnapshotTime)

    val persistenceProvider = new MockDomainPersistenceProvider(domainFqn)
    val persistenceManager = new MockDomainPersistenceManager(Map(domainFqn -> persistenceProvider))

    Mockito.when(persistenceProvider.collectionStore.getOrCreateCollection(collectionId)).thenReturn(Success(Collection(
      collectionId,
      collectionId,
      overrideSnapshotConfig = true,
      ModelSnapshotConfig(snapshotsEnabled = false, triggerByVersion = false, limitedByVersion = false, 0, 0, triggerByTime = false, limitedByTime = false, Duration.ofSeconds(0), Duration.ofSeconds(0)),
      CollectionPermissions(create = true, read = true, write = true, remove = true, manage = true))))
    Mockito.when(persistenceProvider.collectionStore.ensureCollectionExists(Matchers.any())).thenReturn(Success(()))

    Mockito.when(persistenceProvider.collectionStore.collectionExists(collectionId)).thenReturn(Success(true))

    Mockito.when(persistenceProvider.modelPermissionsStore.getCollectionWorldPermissions(collectionId)).thenReturn(Success(CollectionPermissions(create = true, read = true, write = true, remove = true, manage = true)))
    Mockito.when(persistenceProvider.modelPermissionsStore.getAllCollectionUserPermissions(collectionId)).thenReturn(Success(Map[DomainUserId, CollectionPermissions]()))
    Mockito.when(persistenceProvider.modelPermissionsStore.getCollectionUserPermissions(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Success(None))
    Mockito.when(persistenceProvider.modelPermissionsStore.updateModelUserPermissions(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Success(()))
    Mockito.when(persistenceProvider.modelPermissionsStore.updateAllModelUserPermissions(Matchers.any(), Matchers.any())).thenReturn(Success(()))
    Mockito.when(persistenceProvider.modelPermissionsStore.modelOverridesCollectionPermissions(modelId)).thenReturn(Success(false))
    Mockito.when(persistenceProvider.modelPermissionsStore.getModelWorldPermissions(modelId)).thenReturn(Success(ModelPermissions(read = true, write = true, remove = true, manage = true)))
    Mockito.when(persistenceProvider.modelPermissionsStore.getAllModelUserPermissions(modelId)).thenReturn(Success(Map[DomainUserId, ModelPermissions]()))

    val modelPermissionsResolver: ModelPermissionResolver = mock[ModelPermissionResolver]
    Mockito.when(modelPermissionsResolver.getModelAndCollectionPermissions(any(), any(), any()))
      .thenReturn(Success(RealTimeModelPermissions(
        overrideCollection = false,
        CollectionPermissions(create = true, read = true, write = true, remove = true, manage = true),
        Map(),
        ModelPermissions(read = true, write = true, remove = true, manage = true),
        Map())))

    val noModelId = "non existent model"
    Mockito.when(persistenceProvider.modelStore.modelExists(noModelId)).thenReturn(Success(false))
    Mockito.when(persistenceProvider.modelStore.getAndIncrementNextValuePrefix(any())).thenReturn(Success(1L))

    val modelCreator: ModelCreator = mock[ModelCreator]
    val shardRegion: TestProbe[RealtimeModelActor.Message] = testKit.createTestProbe[RealtimeModelActor.Message]()
    val shard: TestProbe[ClusterSharding.ShardCommand] = testKit.createTestProbe[ClusterSharding.ShardCommand]()
    val clientDataResponseTimeout: FiniteDuration = FiniteDuration(100, TimeUnit.MILLISECONDS)
    val receiveTimeout: FiniteDuration = FiniteDuration(100, TimeUnit.MILLISECONDS)
    val resyncTimeout: FiniteDuration = FiniteDuration(100, TimeUnit.MILLISECONDS)

    private val behavior = RealtimeModelActor(
      shardRegion.ref,
      shard.ref,
      modelPermissionsResolver,
      modelCreator,
      persistenceManager,
      clientDataResponseTimeout,
      receiveTimeout,
      resyncTimeout)

    val realtimeModelActor: ActorRef[RealtimeModelActor.Message] = testKit.spawn(behavior)
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
    val replyTo1: TestProbe[RealtimeModelActor.OpenRealtimeModelResponse] = testKit.createTestProbe()
    val client1: TestProbe[ModelClientActor.OutgoingMessage] = testKit.createTestProbe()

    realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), skU1S1, client1.ref, replyTo1.ref)
    val client1OpenResponse: RealtimeModelActor.OpenRealtimeModelResponse =
      replyTo1.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
    assert(client1OpenResponse.response.isRight)
  }

  trait TwoOpenClients extends OneOpenClient {
    val replyTo2: TestProbe[RealtimeModelActor.OpenRealtimeModelResponse] = testKit.createTestProbe()
    val client2: TestProbe[ModelClientActor.OutgoingMessage] = testKit.createTestProbe()

    realtimeModelActor ! RealtimeModelActor.OpenRealtimeModelRequest(domainFqn, modelId, Some(1), DomainUserSessionId(session2, uid2), client2.ref, replyTo2.ref)
    val client2OpenResponse: RealtimeModelActor.OpenRealtimeModelResponse =
      replyTo2.expectMessageType[RealtimeModelActor.OpenRealtimeModelResponse](FiniteDuration(1, TimeUnit.SECONDS))
    assert(client2OpenResponse.response.isRight)
    client1.expectMessageType[ModelClientActor.RemoteClientOpened](FiniteDuration(1, TimeUnit.SECONDS))
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
      .thenReturn(Success(Model(ModelMetaData(collectionId, noModelId, 0, now, now, overridePermissions = true, modelPermissions, 1), modelJsonData)))

    Mockito.when(persistenceProvider.modelStore.modelExists(modelId)).thenReturn(Success(false))
  }

}
