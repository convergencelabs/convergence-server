package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.domain.ModelOperationProcessor
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.model.RealtimeModelActor.ForceClose
import com.convergencelabs.server.domain.model.RealtimeModelActor.OperationCommitted
import com.convergencelabs.server.domain.model.RealtimeModelActor.StreamFailure

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import grizzled.slf4j.Logging

object RealtimeModelPersistenceStream {
  sealed trait ModelPersistenceCommand
  case class ProcessOperatio(op: NewModelOperation) extends ModelPersistenceCommand
  case object ExecuteSnapshot extends ModelPersistenceCommand
}

class RealtimeModelPersistenceStream(
  private[this] val domainFqn: DomainFqn,
  private[this] val modelId: String,
  private[this] val model: ActorRef,
  private[this] implicit val system: ActorSystem,
  private[this] val modelStore: ModelStore,
  private[this] val modelSnapshotStore: ModelSnapshotStore,
  private[this] val modelOperationProcessor: ModelOperationProcessor)
    extends Logging {

  import RealtimeModelPersistenceStream._

  implicit val materializer = ActorMaterializer()

  val streamActor: ActorRef =
    Flow[ModelPersistenceCommand]
      .map {
        case ProcessOperatio(modelOperation) =>
          processOperation(modelOperation)
        case ExecuteSnapshot =>
          executeSnapshot()
      }.to(Sink.onComplete {
        case Success(_) =>
          // Note when we shut down we complete the persistence stream.
          debug(s"Persistence stream completed successfully ${domainFqn}/${modelId}")
        case Failure(f) =>
          // FIXME this is probably altering state outside of the thread.
          // probably need to send a message.
          error("Persistence stream completed with an error", f)
          model ! StreamFailure
      }).runWith(Source
        .actorRef[ModelPersistenceCommand](bufferSize = 1000, OverflowStrategy.fail))

  private[this] def processOperation(modelOperation: NewModelOperation): Unit = {
    modelOperationProcessor.processModelOperation(modelOperation)
      .map { _ =>
        model ! OperationCommitted(modelOperation.version)
      }
      .recover {
        case cause: Exception =>
          // FIXME this is probably altering state outside of the thread.
          // probably need to send a message.
          error(s"Error applying operation: ${modelOperation}", cause)
          model ! ForceClose("There was an unexpected persistence error applying an operation.")
      }
  }

  private[this] def executeSnapshot(): Unit = {
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