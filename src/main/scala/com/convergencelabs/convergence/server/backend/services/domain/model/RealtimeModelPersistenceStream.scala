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

package com.convergencelabs.convergence.server.backend.services.domain.model

import akka.actor.typed.ActorSystem
import akka.actor.{ActorRef, Status, ActorSystem => ClassicActorSystem}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.actor.typed.scaladsl.adapter._
import akka.stream.{CompletionStrategy, OverflowStrategy}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.{ModelOperationProcessor, ModelSnapshotStore, ModelStore}
import com.convergencelabs.convergence.server.backend.services.domain.model.RealtimeModelPersistence.PersistenceEventHandler
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.model.{ModelSnapshot, ModelSnapshotMetaData}
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

private[model] object RealtimeModelPersistence {

  trait PersistenceEventHandler {
    def onError(message: String): Unit

    def onClosed(): Unit

    def onOperationCommitted(version: Long): Unit

    def onOperationError(message: String): Unit
  }

}

private[model] trait RealtimeModelPersistence {
  def processOperation(op: NewModelOperation): Unit

  def executeSnapshot(): Unit

  def close(): Unit
}

private[model] object RealtimeModelPersistenceStream {

  sealed trait ModelPersistenceCommand

  case class ProcessOperation(op: NewModelOperation) extends ModelPersistenceCommand

  case object ExecuteSnapshot extends ModelPersistenceCommand

}

private[model] final class RealtimeModelPersistenceStream(handler: PersistenceEventHandler,
                                                          domainId: DomainId,
                                                          modelId: String,
                                                          system: ActorSystem[_],
                                                          modelStore: ModelStore,
                                                          modelSnapshotStore: ModelSnapshotStore,
                                                          modelOperationProcessor: ModelOperationProcessor)
  extends RealtimeModelPersistence
    with Logging {

  import RealtimeModelPersistenceStream._

  private[this] implicit val s: ClassicActorSystem = system.toClassic

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
          case akka.actor.Status.Success(s: CompletionStrategy) => s
          case akka.actor.Status.Success(_) => CompletionStrategy.draining
          case akka.actor.Status.Success => CompletionStrategy.draining
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
    (for {
      maybeModel <- modelStore.getModel(this.modelId)
      modelData <- maybeModel.map(Success(_))
        .getOrElse(Failure(new IllegalStateException("The model was not found when taking a snapshot")))
      snapshotMetaData <- Try {
        val snapshotMetaData = ModelSnapshotMetaData(
          modelId,
          modelData.metaData.version,
          modelData.metaData.modifiedTime)

        val snapshot = ModelSnapshot(snapshotMetaData, modelData.data)
        modelSnapshotStore.createSnapshot(snapshot)
        snapshotMetaData
      }
    } yield {
      logger.debug(s"$domainId/$modelId: Snapshot successfully taken for model " +
        s"at version: ${snapshotMetaData.version}, timestamp: ${snapshotMetaData.timestamp}")
    }).recover {
      case cause: Throwable =>
        logger.error(s"$domainId/$modelId: Error taking snapshot of model.", cause)
    }
  }
}

private[model] trait RealtimeModelPersistenceFactory {
  def create(handler: PersistenceEventHandler): RealtimeModelPersistence
}

private[model] class RealtimeModelPersistenceStreamFactory(domainFqn: DomainId,
                                                           modelId: String,
                                                           system: ActorSystem[_],
                                                           modelStore: ModelStore,
                                                           modelSnapshotStore: ModelSnapshotStore,
                                                           modelOperationProcessor: ModelOperationProcessor)
  extends RealtimeModelPersistenceFactory {

  def create(handler: PersistenceEventHandler): RealtimeModelPersistence = {
    new RealtimeModelPersistenceStream(handler, domainFqn, modelId, system, modelStore, modelSnapshotStore, modelOperationProcessor)
  }
}
