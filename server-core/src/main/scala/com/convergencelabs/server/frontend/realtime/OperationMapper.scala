package com.convergencelabs.server.frontend.realtime

import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.Operation
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.convergencelabs.server.frontend.realtime.proto.ArrayInsertOperationData
import com.convergencelabs.server.frontend.realtime.proto.ArrayMoveOperationData
import com.convergencelabs.server.frontend.realtime.proto.ArrayRemoveOperationData
import com.convergencelabs.server.frontend.realtime.proto.ArrayReplaceOperationData
import com.convergencelabs.server.frontend.realtime.proto.ArraySetOperationData
import com.convergencelabs.server.frontend.realtime.proto.CompoundOperationData
import com.convergencelabs.server.frontend.realtime.proto.DiscreteOperationData
import com.convergencelabs.server.frontend.realtime.proto.NumberAddOperationData
import com.convergencelabs.server.frontend.realtime.proto.NumberSetOperationData
import com.convergencelabs.server.frontend.realtime.proto.ObjectAddPropertyOperationData
import com.convergencelabs.server.frontend.realtime.proto.ObjectRemovePropertyOperationData
import com.convergencelabs.server.frontend.realtime.proto.ObjectSetOperationData
import com.convergencelabs.server.frontend.realtime.proto.ObjectSetPropertyOperationData
import com.convergencelabs.server.frontend.realtime.proto.OperationData
import com.convergencelabs.server.frontend.realtime.proto.StringInsertOperationData
import com.convergencelabs.server.frontend.realtime.proto.StringRemoveOperationData
import com.convergencelabs.server.frontend.realtime.proto.StringSetOperationData
import com.convergencelabs.server.frontend.realtime.proto.BooleanSetOperationData
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation

private[realtime] object OperationMapper {

  def mapIncoming(op: OperationData): Operation = {
    op match {
      case operation: CompoundOperationData => mapIncomingCompound(operation)
      case operation: DiscreteOperationData => mapIncomingDiscrete(operation)
    }
  }

  def mapIncomingCompound(op: CompoundOperationData): CompoundOperation = {
    CompoundOperation(op.ops.map(opData => mapIncoming(opData).asInstanceOf[DiscreteOperation]))
  }

  // scalastyle:off cyclomatic.complexity
  def mapIncomingDiscrete(op: DiscreteOperationData): DiscreteOperation = {
    op match {
      case StringInsertOperationData(path, noOp, index, value) => StringInsertOperation(path, noOp, index, value)
      case StringRemoveOperationData(path, noOp, index, value) => StringRemoveOperation(path, noOp, index, value)
      case StringSetOperationData(path, noOp, value) => StringSetOperation(path, noOp, value)

      case ArrayInsertOperationData(path, noOp, idx, newVal) => ArrayInsertOperation(path, noOp, idx, newVal)
      case ArrayRemoveOperationData(path, noOp, idx) => ArrayRemoveOperation(path, noOp, idx)
      case ArrayMoveOperationData(path, noOp, fromIdx, toIdx) => ArrayMoveOperation(path, noOp, fromIdx, toIdx)
      case ArrayReplaceOperationData(path, noOp, idx, newVal) => ArrayReplaceOperation(path, noOp, idx, newVal)
      case ArraySetOperationData(path, noOp, array) => ArraySetOperation(path, noOp, array)

      case ObjectSetPropertyOperationData(path, noOp, prop, newVal) => ObjectSetPropertyOperation(path, noOp, prop, newVal)
      case ObjectAddPropertyOperationData(path, noOp, prop, newVal) => ObjectAddPropertyOperation(path, noOp, prop, newVal)
      case ObjectRemovePropertyOperationData(path, noOp, prop) => ObjectRemovePropertyOperation(path, noOp, prop)
      case ObjectSetOperationData(path, noOp, objectData) => ObjectSetOperation(path, noOp, objectData)

      case NumberAddOperationData(path, noOp, delta) => NumberAddOperation(path, noOp, delta)
      case NumberSetOperationData(path, noOp, number) => NumberSetOperation(path, noOp, number)
      
      case BooleanSetOperationData(path, noOp, value) => BooleanSetOperation(path, noOp, value)
    }
  }
  // scalastyle:on cyclomatic.complexity

  def mapOutgoing(op: Operation): OperationData = {
    op match {
      case operation: CompoundOperation => mapOutgoingCompound(operation)
      case operation: DiscreteOperation => mapOutgoingDiscrete(operation)
    }
  }

  def mapOutgoingCompound(op: CompoundOperation): CompoundOperationData = {
    CompoundOperationData(op.operations.map(opData => mapOutgoing(opData).asInstanceOf[DiscreteOperationData]))
  }

  // scalastyle:off cyclomatic.complexity
  def mapOutgoingDiscrete(op: DiscreteOperation): DiscreteOperationData = {
    op match {
      case StringInsertOperation(path, noOp, index, value) => StringInsertOperationData(path, noOp, index, value)
      case StringRemoveOperation(path, noOp, index, value) => StringRemoveOperationData(path, noOp, index, value)
      case StringSetOperation(path, noOp, value) => StringSetOperationData(path, noOp, value)

      case ArrayInsertOperation(path, noOp, idx, newVal) => ArrayInsertOperationData(path, noOp, idx, newVal)
      case ArrayRemoveOperation(path, noOp, idx) => ArrayRemoveOperationData(path, noOp, idx)
      case ArrayMoveOperation(path, noOp, fromIdx, toIdx) => ArrayMoveOperationData(path, noOp, fromIdx, toIdx)
      case ArrayReplaceOperation(path, noOp, idx, newVal) => ArrayReplaceOperationData(path, noOp, idx, newVal)
      case ArraySetOperation(path, noOp, array) => ArraySetOperationData(path, noOp, array)

      case ObjectSetPropertyOperation(path, noOp, prop, newVal) => ObjectSetPropertyOperationData(path, noOp, prop, newVal)
      case ObjectAddPropertyOperation(path, noOp, prop, newVal) => ObjectAddPropertyOperationData(path, noOp, prop, newVal)
      case ObjectRemovePropertyOperation(path, noOp, prop) => ObjectRemovePropertyOperationData(path, noOp, prop)
      case ObjectSetOperation(path, noOp, objectData) => ObjectSetOperationData(path, noOp, objectData)

      case NumberAddOperation(path, noOp, delta) => NumberAddOperationData(path, noOp, delta)
      case NumberSetOperation(path, noOp, number) => NumberSetOperationData(path, noOp, number)
      
      case BooleanSetOperation(path, noOp, value) => BooleanSetOperationData(path, noOp, value)
    }
  }
  // scalastyle:on cyclomatic.complexity
}
