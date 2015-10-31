package com.convergencelabs.server.domain.model

import akka.testkit.TestKit
import org.scalatest.mock.MockitoSugar
import org.scalatest.BeforeAndAfterAll
import akka.actor.ActorSystem
import org.scalatest.WordSpecLike
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.json4s.JsonAST.JObject
import com.convergencelabs.server.datastore.domain.ModelData
import org.json4s.JsonAST.JString
import com.convergencelabs.server.datastore.domain.ModelMetaData
import com.convergencelabs.server.datastore.domain.SnapshotMetaData
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore
import akka.testkit.TestProbe
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.DomainFqn
import org.mockito.Mockito
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import com.convergencelabs.server.util.MockDomainPersistenceManagerActor
import java.time.Instant
import com.convergencelabs.server.datastore.domain.ModelOperationStore

@RunWith(classOf[JUnitRunner])
class ModelManagerActorSpec
    extends TestKit(ActorSystem("ModelManagerActorSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  val domainPersistenceActor = MockDomainPersistenceManagerActor(system)
  
  // FIXME we need to test that models actually get created and deleted.  Not sure how to do this.
  
  "A ModelManagerActor" when {
    "opening model" must {
      "sucessfully load an unopen model" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest("s1", modelFqn, client.ref), client.ref)
        
        val message = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
        assert(message.modelData == modelData.data)
        assert(message.metaData.version == modelData.metaData.version)
        assert(message.metaData.createdTime == modelData.metaData.createdTime)
        assert(message.metaData.modifiedTime == modelData.metaData.modifiedTime)
      }
      
      "sucessfully load an open model" in new TestFixture {
        val client1 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest("s1", modelFqn, client1.ref), client1.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
        
        val client2 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest("s2", modelFqn, client2.ref), client2.ref)
        val response = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
        
        assert(response.modelData == modelData.data)
        assert(response.metaData.version == modelData.metaData.version)
        assert(response.metaData.createdTime == modelData.metaData.createdTime)
        assert(response.metaData.modifiedTime == modelData.metaData.modifiedTime)
      }
      
      "request data if the model does not exist" in new TestFixture {
        val client1 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest("s1", ModelFqn("no", "model"), client1.ref), client1.ref)
        val response = client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[ClientModelDataRequest])
      }
    }
    
    "requested to create a model" must {
      "return ModelCreated if the model does not exist" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(CreateModelRequest(nonExistentModelFqn, JString("new data")), client.ref)
        client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ModelCreated)
      }
      
      "return ModelAlreadyExists if the model exists" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(CreateModelRequest(modelFqn, JString("new data")), client.ref)
        client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ModelAlreadyExists)
      }
    }
    
    "requested to delete a model" must {
      "return ModelDeleted if the model exists" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(DeleteModelRequest(modelFqn), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ModelDeleted)
      }
      
      "return ModelNotFound if the model does not exist" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(DeleteModelRequest(nonExistentModelFqn), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), ModelNotFound)
      }
    }
  }

  trait TestFixture {
    val nonExistentModelFqn = ModelFqn("collection", "no model")
    val modelFqn = ModelFqn("collection", "model" + System.nanoTime())
    val modelJsonData = JObject("key" -> JString("value"))
    val modelCreateTime = Instant.ofEpochMilli(2L)
    val modelModifiedTime = Instant.ofEpochMilli(3L)
    val modelData = ModelData(ModelMetaData(modelFqn, 1L, modelCreateTime, modelModifiedTime), modelJsonData)
    val modelSnapshotTime = Instant.ofEpochMilli(2L)
    val modelSnapshotMetaData = SnapshotMetaData(modelFqn, 1L, modelSnapshotTime)

    val modelStore = mock[ModelStore]
    Mockito.when(modelStore.modelExists(modelFqn)).thenReturn(true)
    Mockito.when(modelStore.getModelData(modelFqn)).thenReturn(Some(modelData))

    val modelSnapshotStore = mock[ModelSnapshotStore]
    Mockito.when(modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelFqn)).thenReturn(Some(modelSnapshotMetaData))

    val modelOperationStore = mock[ModelOperationStore]
    
    val domainPersistence = mock[DomainPersistenceProvider]
    Mockito.when(domainPersistence.modelStore).thenReturn(modelStore)
    Mockito.when(domainPersistence.modelSnapshotStore).thenReturn(modelSnapshotStore)
    Mockito.when(domainPersistence.modelOperationStore).thenReturn(modelOperationStore)
    
    val domainFqn = DomainFqn("convergence", "default")
    
    domainPersistenceActor.underlyingActor.mockProviders = Map(domainFqn -> domainPersistence)

    val resourceId = "1" + System.nanoTime()
    val domainActor = new TestProbe(system)
    val props = ModelManagerActor.props(
      domainFqn,
      ProtocolConfiguration(100L))

    val modelManagerActor = system.actorOf(props, resourceId)
  }
}