/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import akka.actor.ActorRef
import com.convergencelabs.server.datastore.domain.ModelOperationStore
import com.convergencelabs.server.domain.DomainUserSessionId
import com.convergencelabs.server.domain.model.ot._

import scala.concurrent.{ExecutionContext, Future}

class ModelOperationReplayHelper(private[this] val modelOperationStore: ModelOperationStore,
                                 private[this] val modelId: String,
                                 private[this] val clientActor: ActorRef,
                                 private[this] implicit val sender: ActorRef,
                                 private[this] implicit val ec: ExecutionContext
                                ) {

  def sendOperationsInRange(firstVersion: Long, lastVersion: Long): Future[Unit] = {
    Future {
      // FIXME we need to probably get these in batches. We don't want to just
      //  grab an unbounded set of data.
      modelOperationStore
        .getOperationsInVersionRange(modelId, firstVersion, lastVersion)
        .map(_.foreach(sendOperation)).get
    }
  }

  private[this] def sendOperation(modelOperation: ModelOperation): Unit = {
    val op = convertAppliedOperation(modelOperation.op)
    val outgoingOp = OutgoingOperation(
      modelId,
      DomainUserSessionId(modelOperation.sessionId, modelOperation.userId),
      modelOperation.version - 1, // this needs to be the context version, which is one behind the version
      modelOperation.timestamp,
      op)
    clientActor ! outgoingOp
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
