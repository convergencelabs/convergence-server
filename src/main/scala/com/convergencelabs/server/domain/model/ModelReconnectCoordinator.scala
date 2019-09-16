package com.convergencelabs.server.domain.model

import akka.actor.ActorRef
import com.convergencelabs.server.datastore.domain.ModelOperationStore
import com.convergencelabs.server.domain.DomainUserSessionId
import com.convergencelabs.server.domain.model.ot._

import scala.util.{Success, Try}

class ModelReconnectCoordinator(private[this] val modelOperationStore: ModelOperationStore,
                                private[this] val modelId: String,
                                private[this] val currentContextVersion: Long,
                                private[this] val previousSessionId: String,
                                private[this] val previousContextVersion: Long,
                                private[this] val actor: ActorRef
                               ) {
  def initiateReconnect(): Unit = {
    (for {
      maxSessionOperationVersion <- getMaxVersionForSession
      _ <- sendOperationsUpToMaxForSession(maxSessionOperationVersion)
      _ <- statePathRejoined()
      _ <- sendRemainingOperations(maxSessionOperationVersion)
    } yield {
      actor ! ModelReconnectComplete(modelId)
    })
      .recover {
        case cause: Throwable =>
          actor ! ModelReconnectFailed(modelId, cause.getMessage)
      }
  }

  private[this] def getMaxVersionForSession: Try[Option[Long]] = {
    modelOperationStore.getMaxOperationForSessionAfterVersion(modelId, previousSessionId, previousContextVersion)
  }

  private[this] def sendOperationsUpToMaxForSession(maxVersionForSession: Option[Long]): Try[Unit] = {
    maxVersionForSession match {
      case Some(version) =>
        sendOperationsInRange(previousContextVersion + 1, version)
      case None =>
        Success(())
    }
  }

  private[this] def statePathRejoined(): Try[Unit] = {
    this.actor ! ModelServerStatePathRejoined(modelId)
    Success(())
  }

  private[this] def sendRemainingOperations(maxSessionOperationVersion: Option[Long]): Try[Unit] = {
    val fromVersion = maxSessionOperationVersion.getOrElse(previousContextVersion) + 1
    sendOperationsInRange(fromVersion, currentContextVersion)
  }

  private[this] def sendOperationsInRange(firstVersion: Long, lastVersion: Long): Try[Unit] = {
    // FIXME we need to probably get these in batches. We don't want to just
    //  grab an unbounded set of data.
    modelOperationStore
      .getOperationsInVersionRange(modelId, firstVersion, lastVersion)
      .map(_.foreach(sendOperation))
  }

  private[this] def sendOperation(modelOperation: ModelOperation): Unit = {
    val op = convertAppliedOperation(modelOperation.op)
    val outgoingOp = OutgoingOperation(
      modelId,
      DomainUserSessionId(modelOperation.sessionId, modelOperation.userId),
      modelOperation.version.toInt,
      modelOperation.timestamp,
      op)
    actor ! outgoingOp
  }

  private[this] def convertAppliedOperation(appliedOperation: AppliedOperation): Operation = {
    appliedOperation match {
      case AppliedCompoundOperation(ops) =>
        CompoundOperation(ops.map(convertAppliedOperation(_).asInstanceOf[DiscreteOperation]))
      case AppliedStringRemoveOperation(id, noOp, index, _, oldValue) =>
        StringRemoveOperation(id, noOp, index, oldValue.get)
      case AppliedStringInsertOperation(id, noOp, index, value) =>
        StringInsertOperation(id, noOp, index, value)
      case AppliedStringSetOperation(id, noOp, value, _) =>
        StringSetOperation(id, noOp, value)

      case AppliedObjectSetPropertyOperation(id, noOp, property, value, _) =>
        ObjectSetPropertyOperation(id, noOp, property, value)
      case AppliedObjectAddPropertyOperation(id, noOp, property, value) =>
        ObjectAddPropertyOperation(id, noOp, property, value)
      case AppliedObjectRemovePropertyOperation(id, noOp, property, _) =>
        ObjectRemovePropertyOperation(id, noOp, property)
      case AppliedObjectSetOperation(id, noOp, value, _) =>
        ObjectSetOperation(id, noOp, value)

      case AppliedNumberAddOperation(id, noOp, value) =>
        NumberAddOperation(id, noOp, value)
      case AppliedNumberSetOperation(id, noOp, value, _) =>
        NumberSetOperation(id, noOp, value)

      case AppliedBooleanSetOperation(id, noOp, value, _) =>
        BooleanSetOperation(id, noOp, value)

      case AppliedArrayInsertOperation(id, noOp, index, value) =>
        ArrayInsertOperation(id, noOp, index, value)
      case AppliedArrayRemoveOperation(id, noOp, index, _) =>
        ArrayRemoveOperation(id, noOp, index)
      case AppliedArrayReplaceOperation(id, noOp, index, value, _) =>
        ArrayReplaceOperation(id, noOp, index, value)
      case AppliedArrayMoveOperation(id, noOp, fromIndex, toIndex) =>
        ArrayMoveOperation(id, noOp, fromIndex, toIndex)
      case AppliedArraySetOperation(id, noOp, value, _) =>
        ArraySetOperation(id, noOp, value)

      case AppliedDateSetOperation(id, noOp, value, _) =>
        DateSetOperation(id, noOp, value)
    }
  }
}
