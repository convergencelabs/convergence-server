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

package com.convergencelabs.convergence.server.api.realtime.protocol

import com.convergencelabs.convergence.proto.model.HistoricalOperationsResponseMessage.ModelOperationData
import com.convergencelabs.convergence.proto.model._
import com.convergencelabs.convergence.server.api.realtime.protocol.DataValueProtoConverters._
import com.convergencelabs.convergence.server.backend.services.domain.model.ModelOperation
import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.google.protobuf.timestamp.Timestamp

private[realtime] object ModelOperationConverters {

  def modelOperationToProto(modelOp: ModelOperation): ModelOperationData = {
    val ModelOperation(modelId, version, timestamp, _, sessionId, op) = modelOp

    val mappedOp = op match {
      case operation: AppliedCompoundOperation =>
        AppliedOperationData().withCompoundOperation(compoundOpToProto(operation))
      case operation: AppliedDiscreteOperation =>
        AppliedOperationData().withDiscreteOperation(discreteOpToProto(operation))
    }

    ModelOperationData(
      modelId,
      version,
      Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)),
      sessionId,
      Some(mappedOp))
  }

  private[this] def compoundOpToProto(op: AppliedCompoundOperation): AppliedCompoundOperationData = {
    AppliedCompoundOperationData(op.operations.map(op => discreteOpToProto(op)))
  }

  // scalastyle:off cyclomatic.complexity
  private[this] def discreteOpToProto(op: AppliedDiscreteOperation): AppliedDiscreteOperationData = {
    op match {
      //
      // Strings
      //
      case AppliedStringSpliceOperation(id, noOp, index, _, deletedValue, insertedValue) =>
        AppliedDiscreteOperationData()
          .withStringSpliceOperation(AppliedStringSpliceOperationData(id, noOp, index, deletedValue.getOrElse(""), insertedValue))

      case AppliedStringSetOperation(id, noOp, value, oldValue) =>
        AppliedDiscreteOperationData()
          .withStringSetOperation(AppliedStringSetOperationData(id, noOp, value, oldValue.get))

      //
      // Arrays
      //
      case AppliedArrayInsertOperation(id, noOp, idx, newVal) =>
        AppliedDiscreteOperationData().withArrayInsertOperation(
          AppliedArrayInsertOperationData(id, noOp, idx, Some(dataValueToProto(newVal))))

      case AppliedArrayRemoveOperation(id, noOp, idx, oldValue) =>
        AppliedDiscreteOperationData().withArrayRemoveOperation(
          AppliedArrayRemoveOperationData(id, noOp, idx, oldValue.map(dataValueToProto)))

      case AppliedArrayMoveOperation(id, noOp, fromIdx, toIdx) =>
        AppliedDiscreteOperationData().withArrayMoveOperation(
          AppliedArrayMoveOperationData(id, noOp, fromIdx, toIdx))

      case AppliedArrayReplaceOperation(id, noOp, idx, newVal, oldValue) =>
        AppliedDiscreteOperationData().withArrayReplaceOperation(
          AppliedArrayReplaceOperationData(id, noOp, idx, Some(dataValueToProto(newVal)), oldValue.map(dataValueToProto)))

      case AppliedArraySetOperation(id, noOp, array, oldValue) =>
        AppliedDiscreteOperationData().withArraySetOperation(
          AppliedArraySetOperationData(id, noOp, array.map(dataValueToProto), oldValue.getOrElse(List()).map(dataValueToProto)))

      //
      // Objects
      //
      case AppliedObjectSetPropertyOperation(id, noOp, prop, newVal, oldValue) =>
        AppliedDiscreteOperationData().withObjectSetPropertyOperation(
          AppliedObjectSetPropertyOperationData(id, noOp, prop, Some(dataValueToProto(newVal)), oldValue.map(dataValueToProto)))

      case AppliedObjectAddPropertyOperation(id, noOp, prop, newVal) =>
        AppliedDiscreteOperationData().withObjectAddPropertyOperation(
          AppliedObjectAddPropertyOperationData(id, noOp, prop, Some(dataValueToProto(newVal))))

      case AppliedObjectRemovePropertyOperation(id, noOp, prop, oldValue) =>
        AppliedDiscreteOperationData().withObjectRemovePropertyOperation(
          AppliedObjectRemovePropertyOperationData(id, noOp, prop, oldValue.map(dataValueToProto)))

      case AppliedObjectSetOperation(id, noOp, objectData, oldValue) =>
        val mappedData = objectData.map { case (k, v) => (k, dataValueToProto(v)) }
        val mappedOldValues = oldValue.getOrElse(Map()).map { case (k, v) => (k, dataValueToProto(v)) }
        AppliedDiscreteOperationData().withObjectSetOperation(AppliedObjectSetOperationData(id, noOp, mappedData, mappedOldValues))

      //
      // Numbers
      //
      case AppliedNumberAddOperation(id, noOp, delta) =>
        AppliedDiscreteOperationData().withNumberDeltaOperation(AppliedNumberDeltaOperationData(id, noOp, delta))

      case AppliedNumberSetOperation(id, noOp, number, oldValue) =>
        AppliedDiscreteOperationData().withNumberSetOperation(AppliedNumberSetOperationData(id, noOp, number, oldValue.get))

      //
      // Booleans
      //
      case AppliedBooleanSetOperation(id, noOp, value, oldValue) =>
        AppliedDiscreteOperationData().withBooleanSetOperation(AppliedBooleanSetOperationData(id, noOp, value, oldValue.get))

      //
      // Dates
      //
      case AppliedDateSetOperation(id, noOp, value, oldValue) =>
        AppliedDiscreteOperationData().withDateSetOperation(AppliedDateSetOperationData(id, noOp, Some(Timestamp(value.getEpochSecond, value.getNano)), oldValue.map(v => Timestamp(v.getEpochSecond, v.getNano))))
    }
  }

  // scalastyle:on cyclomatic.complexity
}
