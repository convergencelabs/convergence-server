package com.convergencelabs.server.api.realtime

import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedDiscreteOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation
import io.convergence.proto.operations.applied.AppliedDiscreteOperationData
import io.convergence.proto.operations.applied.AppliedArrayInsertOperationData
import io.convergence.proto.operations.applied.AppliedCompoundOperationData
import io.convergence.proto.operations.applied.AppliedStringSetOperationData
import io.convergence.proto.operations.applied.AppliedStringInsertOperationData
import io.convergence.proto.operations.applied.AppliedNumberDeltaOperationData
import io.convergence.proto.operations.applied.AppliedArrayReplaceOperationData
import io.convergence.proto.operations.applied.AppliedArrayRemoveOperationData
import io.convergence.proto.operations.applied.AppliedObjectSetPropertyOperationData
import io.convergence.proto.operations.applied.AppliedStringRemoveOperationData
import io.convergence.proto.operations.applied.AppliedObjectRemovePropertyOperationData
import io.convergence.proto.operations.applied.AppliedNumberSetOperationData
import io.convergence.proto.operations.applied.AppliedBooleanSetOperationData
import io.convergence.proto.operations.applied.AppliedArrayMoveOperationData
import io.convergence.proto.operations.applied.AppliedObjectAddPropertyOperationData
import io.convergence.proto.operations.applied.AppliedObjectSetOperationData
import io.convergence.proto.operations.applied.AppliedDateSetOperationData
import io.convergence.proto.model.ModelOperationData
import io.convergence.proto.operations.applied.AppliedArraySetOperationData
import io.convergence.proto.operations.applied.AppliedDiscreteOperationData
import io.convergence.proto.operations.applied.AppliedOperationData
import com.google.protobuf.timestamp.Timestamp
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.data.DataValue
import ImplicitMessageConversions._

private[realtime] object ModelOperationMapper {

  def mapOutgoing(modelOp: ModelOperation): ModelOperationData = {
    val ModelOperation(modelId, version, timestamp, username, sessionId, op) = modelOp
    val mappedOp = op match {
      case operation: AppliedCompoundOperation =>
        AppliedOperationData().withCompoundOperation(mapOutgoingCompound(operation))
      case operation: AppliedDiscreteOperation =>
        AppliedOperationData().withDiscreteOperation(mapOutgoingDiscrete(operation))
    }
    ModelOperationData(
      modelId,
      version,
      Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)),
      sessionId,
      Some(mappedOp))
  }

  def mapOutgoingCompound(op: AppliedCompoundOperation): AppliedCompoundOperationData = {
    AppliedCompoundOperationData(op.operations.map(op => mapOutgoingDiscrete(op)))
  }

  // scalastyle:off cyclomatic.complexity
  def mapOutgoingDiscrete(op: AppliedDiscreteOperation): AppliedDiscreteOperationData = {
    op match {
      case AppliedStringInsertOperation(id, noOp, index, value) =>
        AppliedDiscreteOperationData().withStringInsertOperation(AppliedStringInsertOperationData(id, noOp, index, value))
      case AppliedStringRemoveOperation(id, noOp, index, length, oldValue) =>
        AppliedDiscreteOperationData().withStringRemoveOperation(AppliedStringRemoveOperationData(id, noOp, index, length, oldValue.get))
      case AppliedStringSetOperation(id, noOp, value, oldValue) =>
        AppliedDiscreteOperationData().withStringSetOperation(AppliedStringSetOperationData(id, noOp, value, oldValue.get))

      case AppliedArrayInsertOperation(id, noOp, idx, newVal) =>
        AppliedDiscreteOperationData().withArrayInsertOperation(AppliedArrayInsertOperationData(id, noOp, idx, Some(newVal)))
      case AppliedArrayRemoveOperation(id, noOp, idx, oldValue) =>
        AppliedDiscreteOperationData().withArrayRemoveOperation(AppliedArrayRemoveOperationData(id, noOp, idx, oldValue.map(dataValueToMessage(_))))
      case AppliedArrayMoveOperation(id, noOp, fromIdx, toIdx) =>
        AppliedDiscreteOperationData().withArrayMoveOperation(AppliedArrayMoveOperationData(id, noOp, fromIdx, toIdx))
      case AppliedArrayReplaceOperation(id, noOp, idx, newVal, oldValue) =>
        AppliedDiscreteOperationData().withArrayReplaceOperation(AppliedArrayReplaceOperationData(id, noOp, idx, Some(newVal), oldValue.map(dataValueToMessage(_))))
      case AppliedArraySetOperation(id, noOp, array, oldValue) =>
        AppliedDiscreteOperationData().withArraySetOperation(
          AppliedArraySetOperationData(id, noOp, array.map(dataValueToMessage(_)), oldValue.getOrElse(List()).map(dataValueToMessage(_)).toSeq))

      case AppliedObjectSetPropertyOperation(id, noOp, prop, newVal, oldValue) =>
        AppliedDiscreteOperationData().withObjectSetPropertyOperation(
          AppliedObjectSetPropertyOperationData(id, noOp, prop, Some(newVal), oldValue.map(dataValueToMessage(_))))
      case AppliedObjectAddPropertyOperation(id, noOp, prop, newVal) =>
        AppliedDiscreteOperationData().withObjectAddPropertyOperation(
          AppliedObjectAddPropertyOperationData(id, noOp, prop, Some(newVal)))
      case AppliedObjectRemovePropertyOperation(id, noOp, prop, oldValue) =>
        AppliedDiscreteOperationData().withObjectRemovePropertyOperation(
          AppliedObjectRemovePropertyOperationData(id, noOp, prop, oldValue.map(dataValueToMessage(_))))
      case AppliedObjectSetOperation(id, noOp, objectData, oldValue) =>
        val mappedData = objectData.map { case (k, v) => (k, dataValueToMessage(v)) }
        val mappedOldValues = oldValue.getOrElse(Map()).map { case (k, v) => (k, dataValueToMessage(v)) }
        AppliedDiscreteOperationData().withObjectSetOperation(AppliedObjectSetOperationData(id, noOp, mappedData, mappedOldValues))
      case AppliedNumberAddOperation(id, noOp, delta) =>
        AppliedDiscreteOperationData().withNumberDeltaOperation(AppliedNumberDeltaOperationData(id, noOp, delta))
      case AppliedNumberSetOperation(id, noOp, number, oldValue) =>
        AppliedDiscreteOperationData().withNumberSetOperation(AppliedNumberSetOperationData(id, noOp, number, oldValue.get))

      case AppliedBooleanSetOperation(id, noOp, value, oldValue) =>
        AppliedDiscreteOperationData().withBooleanSetOperation(AppliedBooleanSetOperationData(id, noOp, value, oldValue.get))
      case AppliedDateSetOperation(id, noOp, value, oldValue) =>
        AppliedDiscreteOperationData().withDateSetOperation(AppliedDateSetOperationData(id, noOp, Some(Timestamp(value.getEpochSecond, value.getNano)), oldValue.map(v => Timestamp(v.getEpochSecond, v.getNano))))
    }
  }
  // scalastyle:on cyclomatic.complexity
}
