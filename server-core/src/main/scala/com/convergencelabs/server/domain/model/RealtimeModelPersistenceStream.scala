package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.domain.ModelOperationProcessor
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import grizzled.slf4j.Logging
import com.convergencelabs.server.domain.model.RealtimeModelPersistence.PersistenceEventHanlder
import akka.actor.Status

trait RealtimeModelPersistenceFactory {
  def create(handler: PersistenceEventHanlder): RealtimeModelPersistence;
}

object RealtimeModelPersistence {
  trait PersistenceEventHanlder {
    def onError(message: String): Unit
    def onClosed(): Unit
    def onOperationCommited(version: Long): Unit
    def onOperationError(message: String): Unit
  }
}

trait RealtimeModelPersistence {
  def processOperation(op: NewModelOperation): Unit
  def executeSnapshot(): Unit
  def close(): Unit
}

object RealtimeModelPersistenceStream {
  sealed trait ModelPersistenceCommand
  case class ProcessOperation(op: NewModelOperation) extends ModelPersistenceCommand
  case object ExecuteSnapshot extends ModelPersistenceCommand

}

class RealtimeModelPersistenceStream(
  private[this] val handler: PersistenceEventHanlder,
  private[this] val domainFqn: DomainFqn,
  private[this] val modelId: String,
  private[this] implicit val system: ActorSystem,
  private[this] val modelStore: ModelStore,
  private[this] val modelSnapshotStore: ModelSnapshotStore,
  private[this] val modelOperationProcessor: ModelOperationProcessor)
    extends RealtimeModelPersistence
    with Logging {

  import RealtimeModelPersistenceStream._

  private[this] implicit val materializer = ActorMaterializer()
  
  debug(s"Persistence stream started ${domainFqn}/${modelId}")

  def processOperation(op: NewModelOperation): Unit = {
    streamActor ! ProcessOperation(op)
  }

  def executeSnapshot(): Unit = {
    streamActor ! ExecuteSnapshot
  }

  def close(): Unit = {
    streamActor ! Status.Success(())
  }

  private[this] val streamActor: ActorRef =
    Flow[ModelPersistenceCommand]
      .map {
        case ProcessOperation(modelOperation) =>
          onProcessOperation(modelOperation)
        case ExecuteSnapshot =>
          onExecuteSnapshot()
      }.to(Sink.onComplete {
        case Success(_) =>
          debug(s"Persistence stream completed successfully ${domainFqn}/${modelId}")
          handler.onClosed()
        case Failure(cause) =>
          error("Persistence stream completed with an error", cause)
          handler.onError("There was an unexpected error in the persistence stream")
      }).runWith(Source
        .actorRef[ModelPersistenceCommand](bufferSize = 1000, OverflowStrategy.fail))

  private[this] def onProcessOperation(modelOperation: NewModelOperation): Unit = {
    modelOperationProcessor.processModelOperation(modelOperation)
      .map (_ => handler.onOperationCommited(modelOperation.version))
      .recover {
        case cause: Throwable =>
          error(s"Error applying operation: ${modelOperation}", cause)
          handler.onOperationError("There was an unexpected persistence error applying an operation.")
          ()
      }
  }

  private[this] def onExecuteSnapshot(): Unit = {
    Try {
      // FIXME: Handle Failure from try and None from option.
      val modelData = modelStore.getModel(this.modelId).get.get
      val snapshotMetaData = new ModelSnapshotMetaData(
        modelId,
        modelData.metaData.version,
        modelData.metaData.modifiedTime)

      val snapshot = new ModelSnapshot(snapshotMetaData, modelData.data)
      modelSnapshotStore.createSnapshot(snapshot)
      snapshotMetaData
    } map { snapshotMetaData =>
      debug(s"Snapshot successfully taken for model: '${domainFqn}/${modelId}' " +
        s"at version: ${snapshotMetaData.version}, timestamp: ${snapshotMetaData.timestamp}")
    } recover {

      case cause: Throwable =>
        error(s"Error taking snapshot of model (${modelId})", cause)
    }
  }
}

class RealtimeModelPersistenceStreamFactory(
  private[this] val domainFqn: DomainFqn,
  private[this] val modelId: String,
  private[this] implicit val system: ActorSystem,
  private[this] val modelStore: ModelStore,
  private[this] val modelSnapshotStore: ModelSnapshotStore,
  private[this] val modelOperationProcessor: ModelOperationProcessor)
    extends RealtimeModelPersistenceFactory {

  def create(handler: PersistenceEventHanlder): RealtimeModelPersistence = {
    new RealtimeModelPersistenceStream(handler, domainFqn, modelId, system, modelStore, modelSnapshotStore, modelOperationProcessor)
  }
}