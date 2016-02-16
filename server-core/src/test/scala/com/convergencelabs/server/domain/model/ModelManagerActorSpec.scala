package com.convergencelabs.server.domain.model

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Success
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import com.convergencelabs.server.HeartbeatConfiguration
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.domain.DomainConfigStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.ModelOperationStore
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.util.MockDomainPersistenceManagerActor
import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe
import com.convergencelabs.server.datastore.domain.CollectionStore

@RunWith(classOf[JUnitRunner])
class ModelManagerActorSpec
    extends TestKit(ActorSystem("ModelManagerActorSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val domainPersistenceActor = MockDomainPersistenceManagerActor(system)

  // FIXME we need to test that models actually get created and deleted.  Not sure how to do this.

  "A ModelManagerActor" when {
    "opening model" must {
      "sucessfully load an unopen model" in new TestFixture {
        val client = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(userId1, sessionId1, modelFqn, true, client.ref), client.ref)

        val message = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])
        assert(message.modelData == modelData.data)
        assert(message.metaData.version == modelData.metaData.version)
        assert(message.metaData.createdTime == modelData.metaData.createdTime)
        assert(message.metaData.modifiedTime == modelData.metaData.modifiedTime)
      }

      "sucessfully load an open model" in new TestFixture {
        val client1 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(userId1, sessionId1, modelFqn, true, client1.ref), client1.ref)
        client1.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        val client2 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(userId2, sessionId1, modelFqn, true, client2.ref), client2.ref)
        val response = client2.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[OpenModelSuccess])

        assert(response.modelData == modelData.data)
        assert(response.metaData.version == modelData.metaData.version)
        assert(response.metaData.createdTime == modelData.metaData.createdTime)
        assert(response.metaData.modifiedTime == modelData.metaData.modifiedTime)
      }

      "request data if the model does not exist" in new TestFixture {
        val client1 = new TestProbe(system)
        modelManagerActor.tell(OpenRealtimeModelRequest(userId1, sessionId1, nonExistentModelFqn, true, client1.ref), client1.ref)
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
    val userId1 = "u1";
    val userId2 = "u2";
    val sessionId1 = "1";
    val collectionId = "collection"

    val nonExistentModelFqn = ModelFqn(collectionId, "no model")
    val modelFqn = ModelFqn(collectionId, "model" + System.nanoTime())
    val modelJsonData = JObject("key" -> JString("value"))
    val modelCreateTime = Instant.ofEpochMilli(2L)
    val modelModifiedTime = Instant.ofEpochMilli(3L)
    val modelData = Model(ModelMetaData(modelFqn, 1L, modelCreateTime, modelModifiedTime), modelJsonData)
    val modelSnapshotTime = Instant.ofEpochMilli(2L)
    val modelSnapshotMetaData = ModelSnapshotMetaData(modelFqn, 1L, modelSnapshotTime)

    val modelStore = mock[ModelStore]
    Mockito.when(modelStore.modelExists(modelFqn)).thenReturn(Success(true))
    Mockito.when(modelStore.modelExists(nonExistentModelFqn)).thenReturn(Success(false))
    Mockito.when(modelStore.getModel(modelFqn)).thenReturn(Success(Some(modelData)))

    val modelSnapshotStore = mock[ModelSnapshotStore]
    Mockito.when(modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelFqn)).thenReturn(Success(Some(modelSnapshotMetaData)))

    val modelOperationStore = mock[ModelOperationStore]

    val domainConfigStore = mock[DomainConfigStore]
    Mockito.when(domainConfigStore.getModelSnapshotConfig()).thenReturn(Success(ModelSnapshotConfig(
      false,
      true,
      true,
      250,
      500,
      false,
      false,
      Duration.of(0, ChronoUnit.MINUTES),
      Duration.of(0, ChronoUnit.MINUTES))))

    val collectionStore = mock[CollectionStore]
    Mockito.when(collectionStore.getOrCreateCollection(collectionId))
      .thenReturn(Success(Collection(collectionId, "", false, None)))

    val domainPersistence = mock[DomainPersistenceProvider]
    Mockito.when(domainPersistence.modelStore).thenReturn(modelStore)
    Mockito.when(domainPersistence.modelSnapshotStore).thenReturn(modelSnapshotStore)
    Mockito.when(domainPersistence.modelOperationStore).thenReturn(modelOperationStore)
    Mockito.when(domainPersistence.configStore).thenReturn(domainConfigStore)
    Mockito.when(domainPersistence.collectionStore).thenReturn(collectionStore)

    val domainFqn = DomainFqn("convergence", "default")

    domainPersistenceActor.underlyingActor.mockProviders = Map(domainFqn -> domainPersistence)

    val resourceId = "1" + System.nanoTime()
    val domainActor = new TestProbe(system)
    val protocolConfig = ProtocolConfiguration(
      100 millis,
      HeartbeatConfiguration(
        true,
        5 seconds,
        10 seconds))

    val props = ModelManagerActor.props(domainFqn, protocolConfig)

    val modelManagerActor = system.actorOf(props, resourceId)
  }
}
