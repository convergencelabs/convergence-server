package com.convergencelabs.server.frontend.realtime

import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
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
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JDouble
import com.convergencelabs.server.domain.model.ot.DateSetOperation
import convergence.protocol.operations.ArrayReplaceOperationData
import convergence.protocol.operations.ArraySetOperationData
import convergence.protocol.operations.ObjectAddPropertyOperationData
import convergence.protocol.operations.CompoundOperationData
import convergence.protocol.operations.DateSetOperationData
import convergence.protocol.operations.ArrayMoveOperationData
import convergence.protocol.operations.ArrayRemoveOperationData
import convergence.protocol.operations.ObjectSetPropertyOperationData
import convergence.protocol.operations.ObjectSetOperationData
import convergence.protocol.operations.StringInsertOperationData
import convergence.protocol.operations.StringRemoveOperationData
import convergence.protocol.operations.NumberSetOperationData
import convergence.protocol.operations.BooleanSetOperationData
import convergence.protocol.operations.ObjectRemovePropertyOperationData
import convergence.protocol.operations.NumberAddOperationData
import convergence.protocol.operations.ArrayInsertOperationData
import convergence.protocol.operations.StringSetOperationData
import convergence.protocol.operations.DiscreteOperationData

private[realtime] object OperationMapper {

  def mapIncoming(op: OperationData): Operation = {
    op match {
      case operation: CompoundOperationData => mapIncomingCompound(operation)
      case operation: DiscreteOperationData => mapIncomingDiscrete(operation)
    }
  }

  def mapIncomingCompound(op: CompoundOperationData): CompoundOperation = {
    CompoundOperation(op.o.map(opData => mapIncoming(opData).asInstanceOf[DiscreteOperation]))
  }

  // scalastyle:off cyclomatic.complexity
  def mapIncomingDiscrete(op: DiscreteOperationData): DiscreteOperation = {
    op match {
      case StringInsertOperationData(id, noOp, index, value) => StringInsertOperation(id, noOp, index, value)
      case StringRemoveOperationData(id, noOp, index, value) => StringRemoveOperation(id, noOp, index, value)
      case StringSetOperationData(id, noOp, value) => StringSetOperation(id, noOp, value)

      case ArrayInsertOperationData(id, noOp, idx, newVal) => ArrayInsertOperation(id, noOp, idx, newVal)
      case ArrayRemoveOperationData(id, noOp, idx) => ArrayRemoveOperation(id, noOp, idx)
      case ArrayMoveOperationData(id, noOp, fromIdx, toIdx) => ArrayMoveOperation(id, noOp, fromIdx, toIdx)
      case ArrayReplaceOperationData(id, noOp, idx, newVal) => ArrayReplaceOperation(id, noOp, idx, newVal)
      case ArraySetOperationData(id, noOp, array) => ArraySetOperation(id, noOp, array)

      case ObjectSetPropertyOperationData(id, noOp, prop, newVal) => ObjectSetPropertyOperation(id, noOp, prop, newVal)
      case ObjectAddPropertyOperationData(id, noOp, prop, newVal) => ObjectAddPropertyOperation(id, noOp, prop, newVal)
      case ObjectRemovePropertyOperationData(id, noOp, prop) => ObjectRemovePropertyOperation(id, noOp, prop)
      case ObjectSetOperationData(id, noOp, objectData) => ObjectSetOperation(id, noOp, objectData)

      case NumberAddOperationData(id, noOp, delta) => NumberAddOperation(id, noOp, delta)
      case NumberSetOperationData(id, noOp, number) => NumberSetOperation(id, noOp, number)

      case BooleanSetOperationData(id, noOp, value) => BooleanSetOperation(id, noOp, value)
      
      case DateSetOperationData(id, noOp, value) => DateSetOperation(id, noOp, value)
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
      case StringInsertOperation(id, noOp, index, value) => StringInsertOperationData(id, noOp, index, value)
      case StringRemoveOperation(id, noOp, index, value) => StringRemoveOperationData(id, noOp, index, value)
      case StringSetOperation(id, noOp, value) => StringSetOperationData(id, noOp, value)

      case ArrayInsertOperation(id, noOp, idx, newVal) => ArrayInsertOperationData(id, noOp, idx, newVal)
      case ArrayRemoveOperation(id, noOp, idx) => ArrayRemoveOperationData(id, noOp, idx)
      case ArrayMoveOperation(id, noOp, fromIdx, toIdx) => ArrayMoveOperationData(id, noOp, fromIdx, toIdx)
      case ArrayReplaceOperation(id, noOp, idx, newVal) => ArrayReplaceOperationData(id, noOp, idx, newVal)
      case ArraySetOperation(id, noOp, array) => ArraySetOperationData(id, noOp, array)

      case ObjectSetPropertyOperation(id, noOp, prop, newVal) => ObjectSetPropertyOperationData(id, noOp, prop, newVal)
      case ObjectAddPropertyOperation(id, noOp, prop, newVal) => ObjectAddPropertyOperationData(id, noOp, prop, newVal)
      case ObjectRemovePropertyOperation(id, noOp, prop) => ObjectRemovePropertyOperationData(id, noOp, prop)
      case ObjectSetOperation(id, noOp, objectData) => ObjectSetOperationData(id, noOp, objectData)

      case NumberAddOperation(id, noOp, delta) => NumberAddOperationData(id, noOp, delta)
      case NumberSetOperation(id, noOp, number) => NumberSetOperationData(id, noOp, number)

      case BooleanSetOperation(id, noOp, value) => BooleanSetOperationData(id, noOp, value)
      
      case DateSetOperation(id, noOp, value) => DateSetOperationData(id, noOp, value)
    }
  }
  // scalastyle:on cyclomatic.complexity
}
