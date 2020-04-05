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

import akka.actor.{ActorRef, ActorSystem, Status}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.convergencelabs.convergence.server.datastore.domain.{ModelOperationProcessor, ModelSnapshotStore, ModelStore}
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.model.RealtimeModelPersistence.PersistenceEventHandler
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

trait RealtimeModelPersistenceFactory {
  def create(handler: PersistenceEventHandler): RealtimeModelPersistence
}

object RealtimeModelPersistence {

  trait PersistenceEventHandler {
    def onError(message: String): Unit

    def onClosed(): Unit

    def onOperationCommitted(version: Long): Unit

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

class RealtimeModelPersistenceStream(private[this] val handler: PersistenceEventHandler,
                                     private[this] val domainId: DomainId,
                                     private[this] val modelId: String,
                                     private[this] implicit val system: ActorSystem,
                                     private[this] val modelStore: ModelStore,
                                     private[this] val modelSnapshotStore: ModelSnapshotStore,
                                     private[this] val modelOperationProcessor: ModelOperationProcessor)
  extends RealtimeModelPersistence
    with Logging {

  import RealtimeModelPersistenceStream._

  logger.debug(s"Persistence stream started $domainId/$modelId")

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
        logger.debug(s"$domainId/$modelId: Persistence stream completed successfully.")
        handler.onClosed()
      case Failure(cause) =>
        logger.error(s"$domainId/$modelId: Persistence stream completed with an error.", cause)
        handler.onError("There was an unexpected error in the persistence stream")
    }).runWith(Source
      .actorRef[ModelPersistenceCommand](
        {
          case _ => CompletionStrategy.draining
        }: PartialFunction[Any, CompletionStrategy], {
          case akka.actor.Status.Failure(cause) => cause
        }: PartialFunction[Any, Throwable],
        bufferSize = 1000,
        OverflowStrategy.fail))

  private[this] def onProcessOperation(modelOperation: NewModelOperation): Unit = {
    modelOperationProcessor.processModelOperation(modelOperation)
      .map(_ => handler.onOperationCommitted(modelOperation.version))
      .recover {
        case cause: Throwable =>
          logger.error(s"$domainId/$modelId: Error applying operation: $modelOperation", cause)
          handler.onOperationError("There was an unexpected persistence error applying an operation.")
          ()
      }
  }

  private[this] def onExecuteSnapshot(): Unit = {
    Try {
      // FIXME: Handle Failure from try and None from option.
      val modelData = modelStore.getModel(this.modelId).get.get
      val snapshotMetaData = ModelSnapshotMetaData(
        modelId,
        modelData.metaData.version,
        modelData.metaData.modifiedTime)

      val snapshot = ModelSnapshot(snapshotMetaData, modelData.data)
      modelSnapshotStore.createSnapshot(snapshot)
      snapshotMetaData
    } map { snapshotMetaData =>
      logger.debug(s"$domainId/$modelId: Snapshot successfully taken for model " +
        s"at version: ${snapshotMetaData.version}, timestamp: ${snapshotMetaData.timestamp}")
    } recover {

      case cause: Throwable =>
        logger.error(s"$domainId/$modelId: Error taking snapshot of model.", cause)
    }
  }
}

class RealtimeModelPersistenceStreamFactory(
                                             private[this] val domainFqn: DomainId,
                                             private[this] val modelId: String,
                                             private[this] implicit val system: ActorSystem,
                                             private[this] val modelStore: ModelStore,
                                             private[this] val modelSnapshotStore: ModelSnapshotStore,
                                             private[this] val modelOperationProcessor: ModelOperationProcessor)
  extends RealtimeModelPersistenceFactory {

  def create(handler: PersistenceEventHandler): RealtimeModelPersistence = {
    new RealtimeModelPersistenceStream(handler, domainFqn, modelId, system, modelStore, modelSnapshotStore, modelOperationProcessor)
  }
}