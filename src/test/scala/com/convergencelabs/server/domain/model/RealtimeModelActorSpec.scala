package com.convergencelabs.server.domain.model

import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem
import akka.testkit.{ TestProbe, TestKit }
import com.convergencelabs.server.datastore.domain._
import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import org.json4s.JsonAST.{ JObject, JString }
import org.mockito.{ ArgumentCaptor, Mockito, Matchers }
import org.mockito.Mockito.{ verify, times }
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }
import scala.concurrent.duration.FiniteDuration
import com.convergencelabs.server.domain.DomainFqn
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import com.convergencelabs.server.ErrorMessage
import com.sun.media.sound.Platform

@RunWith(classOf[JUnitRunner])
class RealtimeModelActorSpec(system: ActorSystem)
    extends TestKit(system)
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  def this() = this(ActorSystem("RealtimeModelActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A RealtimeModelActor" when {
    "opening a closed model" must {
      "load the model from the database if it is persisted" in new MockDatabaseWithModel {

        val client = new TestProbe(system)
        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client.ref), client.ref)

        val message = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])
        assert(message.modelData == modelData.data)
        assert(message.metaData.version == modelData.metaData.version)
        assert(message.metaData.createdTime == modelData.metaData.createdTime)
        assert(message.metaData.modifiedTime == modelData.metaData.modifiedTime)
      }
      
      "notify openers of an initialization errror" in new MockDatabaseWithModel {
        val client = new TestProbe(system)
        
        // Set the database up to bomb
        Mockito.when(modelStore.getModelData(Matchers.any())).thenThrow(new IllegalArgumentException("Invalid model"))
        
        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client.ref), client.ref)
        val message = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ErrorMessage])
      }

      "ask all connecting clients for state if it is not persisted" in new MockDatabaseWithoutModel {
        val client1 = new TestProbe(system)
        val client2 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client1.ref), client1.ref)
        val dataRequest1 = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientModelDataRequest])
        assert(dataRequest1.modelFqn == modelFqn)

        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client2.ref), client2.ref)
        val dataRequest2 = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientModelDataRequest])
        assert(dataRequest1.modelFqn == modelFqn)
      }
      
      "reject a client that does not respond with data" in new MockDatabaseWithoutModel {
        val client1 = new TestProbe(system)
        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client1.ref), client1.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientModelDataRequest])
        client1.expectMsgClass(FiniteDuration(200, TimeUnit.MILLISECONDS), classOf[ErrorMessage])
      }
      
      "reject a client that responds with the wrong message in request to data" in new MockDatabaseWithoutModel {
        val client1 = new TestProbe(system)
        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client1.ref), client1.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientModelDataRequest])
        client1.reply(ErrorMessage)
        client1.expectMsgClass(FiniteDuration(200, TimeUnit.MILLISECONDS), classOf[ErrorMessage])
      }

      "notify all queued clients when data is returned by the first client" in new MockDatabaseWithoutModel {
        val client1 = new TestProbe(system)
        val client2 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client1.ref), client1.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientModelDataRequest])

        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client2.ref), client2.ref)
        client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientModelDataRequest])

        // Now mock that the data is there.
        Mockito.when(modelStore.getModelData(modelFqn)).thenReturn(Some(modelData))
        Mockito.when(modelSnapshotStore.getLatestSnapshotMetaData(modelFqn)).thenReturn(modelSnapshotMetaData)

        client1.reply(ClientModelDataResponse(modelJsonData))
        client2.reply(ClientModelDataResponse(modelJsonData))

        // Verify that both clients got the data.
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])
        val openResponse = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])

        assert(openResponse.modelData == modelJsonData)
        assert(openResponse.metaData.version == modelData.metaData.version)
        assert(openResponse.metaData.createdTime == modelData.metaData.createdTime)
        assert(openResponse.metaData.modifiedTime == modelData.metaData.modifiedTime)

        // Verify that the model and snapshot were created.
        verify(modelStore, times(1)).createModel(
          Matchers.eq(modelFqn),
          Matchers.eq(modelJsonData),
          Matchers.any())

        val snapshotCaptor = ArgumentCaptor.forClass(classOf[SnapshotData])

        verify(modelSnapshotStore, times(1)).addSnapshot(snapshotCaptor.capture())
        val capturedData = snapshotCaptor.getValue
        assert(capturedData.data == modelJsonData)
        assert(capturedData.metaData.fqn == modelFqn)
        assert(capturedData.metaData.version == 0) // since it is newly created.
        assert(capturedData.metaData.timestamp != 0)
      }
    }

    "opening an open model" must {
      "provide different session ids, when opened multiple times by the same client" in new MockDatabaseWithModel {

        val client1 = new TestProbe(system)
        val client2 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client1.ref), client1.ref)
        val open1 = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])

        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client2.ref), client2.ref)
        val open2 = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])

        assert(open1.ccId != open2.ccId)
      }

      "provide different session ids, when opened by different clients" in new MockDatabaseWithModel {
        val client1 = new TestProbe(system)
        val client2 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client1.ref), client1.ref)
        val open1 = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])

        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client2.ref), client2.ref)
        val open2 = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])

        assert(open1.ccId != open2.ccId)
      }
    }

    "closing a closed a model" must {
      "acknowledge the close" in new MockDatabaseWithModel with OneOpenClient {
        realtimeModelActor.tell(CloseRealtimeModelRequest(client1OpenResponse.ccId), client1.ref)
        val closeAck = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[CloseModelAcknowledgement])

        assert(modelFqn == closeAck.modelFqn)
        assert(client1OpenResponse.ccId == closeAck.ccId)
        assert(client1OpenResponse.modelResourceId == closeAck.modelResourceId)
      }
      
      "respond with an error for an invalid ccId" in new MockDatabaseWithModel with OneOpenClient {
        realtimeModelActor.tell(CloseRealtimeModelRequest("invalidccId"), client1.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ErrorMessage])
      }
      
      "notify other connected clients" in new MockDatabaseWithModel {
        val client1 = new TestProbe(system)
        val client2 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client1.ref), client1.ref)
        var client1Response = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])

        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client2.ref), client2.ref)
        var client2Response = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])

        realtimeModelActor.tell(CloseRealtimeModelRequest(client2Response.ccId), client2.ref)
        val closeAck = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[CloseModelAcknowledgement])

        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[RemoteSessionClosed])
      }

      "notify domain when last client disconnects" in new MockDatabaseWithModel {
        val client1 = new TestProbe(system)

        realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client1.ref), client1.ref)
        var client1Response = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])
        realtimeModelActor.tell(CloseRealtimeModelRequest(client1Response.ccId), client1.ref)
        val closeAck = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[CloseModelAcknowledgement])
        modelManagerActor.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ModelShutdownRequest])
      }
    }

    "receiving an operation" must {
      "send an ack back to the submitting client" in new OneOpenClient {
        realtimeModelActor.tell(OperationSubmission(client1OpenResponse.ccId, modelData.metaData.version, StringInsertOperation(List(), false, 1, "1")), client1.ref)
        val opAck = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OperationAcknowledgement])
      }

      "send an operation to other connected clients" in new TwoOpenClients {
        realtimeModelActor.tell(OperationSubmission(client1OpenResponse.ccId, modelData.metaData.version, StringInsertOperation(List(), false, 1, "1")), client1.ref)
        val opAck = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OperationAcknowledgement])

        client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OutgoingOperation])
      }
      
      "close a client that submits an invalid operation" in new TwoOpenClients {
        val badOp = StringInsertOperation(List(), false, 1, "1")
        
        Mockito.when(modelStore.applyOperationToModel(
            Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).thenThrow(new IllegalArgumentException("Invalid Operation"))
        
        realtimeModelActor.tell(OperationSubmission(
            client1OpenResponse.ccId, 
            modelData.metaData.version, 
            badOp), client1.ref)
            
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ModelForceClose])
        client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[RemoteSessionClosed])
      }
    }
    
    "open and a model is deleted" must {
      "force close all clients" in new TwoOpenClients {
        realtimeModelActor.tell(ModelDeleted(), modelManagerActor.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ModelForceClose])
        client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ModelForceClose])
      }
    }
  }

  trait TestFixture {
    val modelFqn = ModelFqn("collection", "model" + System.nanoTime())
    val modelJsonData = JObject("key" -> JString("value"))
    val modelData = ModelData(ModelMetaData(modelFqn, 1L, 2L, 3L), modelJsonData)
    val modelSnapshotMetaData = SnapshotMetaData(modelFqn, 1L, 2L)
    val modelStore = mock[ModelStore]
    val modelSnapshotStore = mock[ModelSnapshotStore]
    val resourceId = "1" + System.nanoTime()
    val modelManagerActor = new TestProbe(system)
    val props = RealtimeModelActor.props(
      modelManagerActor.ref,
      DomainFqn("convergence", "default"),
      modelFqn,
      resourceId,
      modelStore,
      modelSnapshotStore,
      100L,
      SnapshotConfig(true, 3, 3, false, 0, 0))

    val realtimeModelActor = system.actorOf(props, resourceId)
  }

  trait MockDatabaseWithModel extends TestFixture {
    Mockito.when(modelStore.modelExists(modelFqn)).thenReturn(true)
    Mockito.when(modelStore.getModelData(modelFqn)).thenReturn(Some(modelData))
    Mockito.when(modelSnapshotStore.getLatestSnapshotMetaData(modelFqn)).thenReturn(modelSnapshotMetaData)
  }

  trait OneOpenClient extends MockDatabaseWithModel {
    val client1 = new TestProbe(system)
    realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client1.ref), client1.ref)
    val client1OpenResponse = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])
  }

  trait TwoOpenClients extends OneOpenClient {
    val client2 = new TestProbe(system)
    realtimeModelActor.tell(OpenRealtimeModelRequest(modelFqn, client2.ref), client2.ref)
    val client2OpenResponse = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelResponse])
  }

  trait MockDatabaseWithoutModel extends TestFixture {
    Mockito.when(modelStore.modelExists(modelFqn)).thenReturn(false)
  }

}