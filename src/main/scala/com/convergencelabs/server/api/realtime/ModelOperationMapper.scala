package com.convergencelabs.server.api.realtime

import com.convergencelabs.convergence.proto.model.HistoricalOperationsResponseMessage.ModelOperationData
import com.convergencelabs.convergence.proto.model._
import com.convergencelabs.server.api.realtime.ImplicitMessageConversions._
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.ot._
import com.google.protobuf.timestamp.Timestamp

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
        AppliedDiscreteOperationData().withArrayRemoveOperation(AppliedArrayRemoveOperationData(id, noOp, idx, oldValue.map(dataValueToMessage)))
      case AppliedArrayMoveOperation(id, noOp, fromIdx, toIdx) =>
        AppliedDiscreteOperationData().withArrayMoveOperation(AppliedArrayMoveOperationData(id, noOp, fromIdx, toIdx))
      case AppliedArrayReplaceOperation(id, noOp, idx, newVal, oldValue) =>
        AppliedDiscreteOperationData().withArrayReplaceOperation(AppliedArrayReplaceOperationData(id, noOp, idx, Some(newVal), oldValue.map(dataValueToMessage)))
      case AppliedArraySetOperation(id, noOp, array, oldValue) =>
        AppliedDiscreteOperationData().withArraySetOperation(
          AppliedArraySetOperationData(id, noOp, array.map(dataValueToMessage), oldValue.getOrElse(List()).map(dataValueToMessage)))

      case AppliedObjectSetPropertyOperation(id, noOp, prop, newVal, oldValue) =>
        AppliedDiscreteOperationData().withObjectSetPropertyOperation(
          AppliedObjectSetPropertyOperationData(id, noOp, prop, Some(newVal), oldValue.map(dataValueToMessage)))
      case AppliedObjectAddPropertyOperation(id, noOp, prop, newVal) =>
        AppliedDiscreteOperationData().withObjectAddPropertyOperation(
          AppliedObjectAddPropertyOperationData(id, noOp, prop, Some(newVal)))
      case AppliedObjectRemovePropertyOperation(id, noOp, prop, oldValue) =>
        AppliedDiscreteOperationData().withObjectRemovePropertyOperation(
          AppliedObjectRemovePropertyOperationData(id, noOp, prop, oldValue.map(dataValueToMessage)))
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
