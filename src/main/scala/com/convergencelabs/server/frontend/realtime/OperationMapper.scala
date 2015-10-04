package com.convergencelabs.server.frontend.realtime

import com.convergencelabs.server.domain.model.ot.ops._
import com.convergencelabs.server.frontend.realtime.proto._

object OperationMapper {

  def mapIncoming(op: OperationData): Operation = {
    op match {
      case operation: CompoundOperationData => mapIncoming(operation)
      case operation: DiscreteOperationData => mapIncoming(operation)
    }
  }

  def mapIncoming(op: CompoundOperationData): CompoundOperation = {
    CompoundOperation(op.ops.map(opData => mapIncoming(opData).asInstanceOf[DiscreteOperation]))
  }

  def mapIncomgin(op: DiscreteOperationData): DiscreteOperation = {
    op match {
      case StringInsertOperationData(path, noOp, index, value) => StringInsertOperation(path, noOp, index, value)
      case StringRemoveOperationData(path, noOp, index, value) => StringDeleteOperation(path, noOp, index, value)
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
    }
  }

  def mapOutgoing(op: Operation): OperationData = {
    op match {
      case operation: CompoundOperation => mapOutgoing(operation)
      case operation: DiscreteOperation => mapOutgoing(operation)
    }
  }

  def mapOutgoing(op: CompoundOperation): CompoundOperationData = {
    CompoundOperationData(op.operations.map(opData => mapOutgoing(opData).asInstanceOf[DiscreteOperationData]))
  }

  def mapOutgoing(op: DiscreteOperation): DiscreteOperationData = {
    op match {
      case StringInsertOperation(path, noOp, index, value) => StringInsertOperationData(path, noOp, index, value)
      case StringDeleteOperation(path, noOp, index, value) => StringRemoveOperationData(path, noOp, index, value)
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
    }
  }
}