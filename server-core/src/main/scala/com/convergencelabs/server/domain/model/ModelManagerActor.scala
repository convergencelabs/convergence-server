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
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.actor.Props
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.ErrorResponse
import java.time.Instant
import java.time.Duration
import java.time.temporal.TemporalUnit
import java.time.temporal.ChronoUnit
import com.convergencelabs.server.domain.ModelSnapshotConfig
import scala.util.Success
import scala.util.Failure

class ModelManagerActor(
  private[this] val domainFqn: DomainFqn,
  private[this] val protocolConfig: ProtocolConfiguration)
    extends Actor with ActorLogging {

  private[this] val openRealtimeModels = mutable.Map[ModelFqn, ActorRef]()
  private[this] var nextModelResourceId: Long = 0

  var persistenceProvider: DomainPersistenceProvider = _

  def receive: Receive = {
    case message: OpenRealtimeModelRequest => onOpenRealtimeModel(message)
    case message: CreateModelRequest => onCreateModelRequest(message)
    case message: DeleteModelRequest => onDeleteModelRequest(message)
    case message: ModelShutdownRequest => onModelShutdownRequest(message)
    case message: Any => unhandled(message)
  }

  private[this] def onOpenRealtimeModel(openRequest: OpenRealtimeModelRequest): Unit = {
    if (!this.openRealtimeModels.contains(openRequest.modelFqn)) {
      val resourceId = "" + nextModelResourceId
      nextModelResourceId += 1

      persistenceProvider.configStore.getModelSnapshotConfig() match {
        case Success(modelSnapshotConfig) => {
          val props = RealtimeModelActor.props(
            self,
            domainFqn,
            openRequest.modelFqn,
            resourceId,
            persistenceProvider.modelStore,
            persistenceProvider.modelOperationProcessor,
            persistenceProvider.modelSnapshotStore,
            5000, // FIXME hard-coded time.  Should this be part of the protocol?
            modelSnapshotConfig)

          val modelActor = context.actorOf(props, resourceId);
          this.openRealtimeModels.put(openRequest.modelFqn, modelActor);
        }
        case Failure(cause) => {
          cause.printStackTrace()
          ??? // FIXME
        }
      }
    }
    // FIXME something above could fail.  Here we just throw an exception.
    // this is not good.
    val modelActor = openRealtimeModels.get(openRequest.modelFqn).get
    modelActor forward openRequest
  }

  private[this] def onCreateModelRequest(createRequest: CreateModelRequest): Unit = {
    persistenceProvider.modelStore.modelExists(createRequest.modelFqn) match {
      case Success(true) => sender ! ModelAlreadyExists
      case Success(false) =>
        val createTime = Instant.now()
        val model = Model(
          ModelMetaData(
            createRequest.modelFqn,
            0,
            createTime,
            createTime),
          createRequest.modelData)

        try {
          // FIXME all of this should work or not, together.
          persistenceProvider.modelStore.createModel(model)
          persistenceProvider.modelSnapshotStore.createSnapshot(
            ModelSnapshot(ModelSnapshotMetaData(createRequest.modelFqn, 0, createTime),
              createRequest.modelData))

          sender ! ModelCreated
        } catch {
          case e: IOException =>
            sender ! ErrorResponse("unknown", "Could not create model: " + e.getMessage)
        }
      case Failure(cause) => ???
    }
  }

  private[this] def onDeleteModelRequest(deleteRequest: DeleteModelRequest): Unit = {
    persistenceProvider.modelStore.modelExists(deleteRequest.modelFqn) match {
      case Success(true) =>
        if (openRealtimeModels.contains(deleteRequest.modelFqn)) {
          openRealtimeModels.remove(deleteRequest.modelFqn).get ! ModelDeleted
        }

        // FIXME do we need to somehow do these in a transaction?
        persistenceProvider.modelStore.deleteModel(deleteRequest.modelFqn)
        persistenceProvider.modelSnapshotStore.removeAllSnapshotsForModel(deleteRequest.modelFqn)
        persistenceProvider.modelOperationStore.removeOperationsForModel(deleteRequest.modelFqn)

        sender ! ModelDeleted
      case Success(false) => sender ! ModelNotFound
      case Failure(cause) => ??? // FIXME
    }
  }

  private[this] def onModelShutdownRequest(shutdownRequest: ModelShutdownRequest): Unit = {
    val fqn = shutdownRequest.modelFqn
    val modelActor = openRealtimeModels.remove(fqn)
    if (!modelActor.isEmpty) {
      modelActor.get ! PoisonPill
    }
  }

  override def postStop(): Unit = {
    log.debug("ModelManagerActor({}) received shutdown command.  Shutting down all Realtime Models.", this.domainFqn)
    openRealtimeModels.clear()
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }

  override def preStart(): Unit = {
    // FIXME Handle none better with logging.
    persistenceProvider = DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn).get
  }
}

object ModelManagerActor {

  val RelativePath = "modelManager"

  def props(domainFqn: DomainFqn,
    protocolConfig: ProtocolConfiguration): Props = Props(
    new ModelManagerActor(
      domainFqn,
      protocolConfig))
}