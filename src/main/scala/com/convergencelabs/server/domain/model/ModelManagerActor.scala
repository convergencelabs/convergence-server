package com.convergencelabs.server.domain.model

import akka.actor.ActorLogging
import akka.actor.Actor
import scala.collection.mutable
import akka.actor.ActorRef
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.ProtocolConfiguration
import akka.actor.PoisonPill
import scala.compat.Platform
import java.io.IOException
import com.convergencelabs.server.datastore.domain.SnapshotData
import com.convergencelabs.server.datastore.domain.SnapshotMetaData
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.actor.Props
import com.convergencelabs.server.ErrorMessage
import com.convergencelabs.server.domain.model.ModelManagerActor.ErrorCodes._
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.SuccessResponse

class ModelManagerActor(
  private[this] val domainFqn: DomainFqn,
  private[this] val protocolConfig: ProtocolConfiguration)
    extends Actor with ActorLogging {

  private[this] val openRealtimeModels = mutable.Map[ModelFqn, ActorRef]()
  private[this] var nextModelResourceId: Long = 0
  
  var persistenceProvider: DomainPersistenceProvider = null

  def receive = {
    case message: OpenRealtimeModelRequest => onOpenRealtimeModel(message)
    case message: CreateModelRequest => onCreateModelRequest(message)
    case message: DeleteModelRequest => onDeleteModelRequest(message)
    case message: ModelShutdownRequest => onModelShutdownRequest(message)
    case message => unhandled(message)
  }

  private[this] def onOpenRealtimeModel(openRequest: OpenRealtimeModelRequest): Unit = {
    if (!persistenceProvider.modelStore.modelExists(openRequest.modelFqn)) {
      sender() ! ErrorMessage(ModelNotFound, "The requested model coult not be opened, because it does not exist.")
      return ;
    }

    if (!this.openRealtimeModels.contains(openRequest.modelFqn)) {
      val resourceId = "" + nextModelResourceId
      nextModelResourceId += 1

      // TODO look this up via model,collection default, then system default
      val snapshotConfig = SnapshotConfig(
        true,
        250,
        500,
        false,
        0,
        0)

      val props = RealtimeModelActor.props(
        self,
        domainFqn,
        openRequest.modelFqn,
        resourceId,
        persistenceProvider.modelStore,
        persistenceProvider.modelSnapshotStore,
        protocolConfig.defaultMessageTimeout,
        snapshotConfig)

      val modelActor = context.actorOf(props, resourceId);
      this.openRealtimeModels.put(openRequest.modelFqn, modelActor);
    }

    val modelActor = openRealtimeModels.get(openRequest.modelFqn).get
    modelActor forward openRequest
  }

  private[this] def onCreateModelRequest(createRequest: CreateModelRequest): Unit = {
    if (persistenceProvider.modelStore.modelExists(createRequest.modelFqn)) {
      sender() ! ErrorMessage(ModelExists, "The model can't be created, because a model with specified colleciton and model already exists.")
    } else {
      val modelData = createRequest.modelData
      val createTime = Platform.currentTime
      try {
        persistenceProvider.modelStore.createModel(
          createRequest.modelFqn,
          modelData,
          createTime)

        persistenceProvider.modelSnapshotStore.addSnapshot(
          SnapshotData(SnapshotMetaData(createRequest.modelFqn, 0, createTime),
            modelData))

        sender() ! SuccessResponse
      } catch {
        case e: IOException =>
          sender() ! ErrorMessage("unknown", "Could not create model: " + e.getMessage)
      }
    }
  }

  private[this] def onDeleteModelRequest(deleteRequest: DeleteModelRequest): Unit = {
    if (persistenceProvider.modelStore.modelExists(deleteRequest.modelFqn)) {

      if (openRealtimeModels.contains(deleteRequest.modelFqn)) {
        openRealtimeModels.remove(deleteRequest.modelFqn).get ! ModelDeleted()
      }

      persistenceProvider.modelStore.deleteModel(deleteRequest.modelFqn)
      persistenceProvider.modelSnapshotStore.removeAllSnapshotsForModel(deleteRequest.modelFqn)
      persistenceProvider.modelHistoryStore.removeHistoryForModel(deleteRequest.modelFqn)

      sender() ! SuccessResponse
    } else {
      sender() ! ErrorMessage(ModelNotFound, "The model could not be deleted because a model with the specified collection and modelId does not exist.")
    }
  }

  private[this] def onModelShutdownRequest(shutdownRequest: ModelShutdownRequest) {
    val fqn = shutdownRequest.modelFqn
    val modelActor = openRealtimeModels.remove(fqn)
    if (!modelActor.isEmpty) {
      modelActor.get ! PoisonPill
    }
  }

  override def postStop(): Unit = {
    log.debug("SharedModelManager({}) received shutdown command.  Shutting down all SharedModels.", this.domainFqn);
    openRealtimeModels.clear();
  }
  
  override def preStart(): Unit = {
    persistenceProvider = DomainPersistenceManagerActor.getPersistenceProvider(self, context, domainFqn)
  }
}

object ModelManagerActor {

  object ErrorCodes {
    val ModelExists = "model_exists"
    val ModelNotFound = "model_not_found"
  }

  val RelativePath = "modelManager"

  def props(domainFqn: DomainFqn,
    protocolConfig: ProtocolConfiguration): Props = Props(
    new ModelManagerActor(
      domainFqn,
      protocolConfig))
}