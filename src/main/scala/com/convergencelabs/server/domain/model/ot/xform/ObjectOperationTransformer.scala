package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation

object ObjectOperationTransformer {
  def transformSetPropertySetProperty(op1: ObjectSetPropertyOperation, op2: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetPropertyRemoveProperty(op1: ObjectSetPropertyOperation, op2: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetPropertySet(op1: ObjectSetPropertyOperation, op2: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformRemovePropertySetProperty(op1: ObjectRemovePropertyOperation, op2: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformRemovePropertyRemoveProperty(op1: ObjectRemovePropertyOperation, op2: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformRemovePropertySet(op1: ObjectRemovePropertyOperation, op2: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetSetProperty(op1: ObjectSetOperation, op2: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetRemoveProperty(op1: ObjectSetOperation, op2: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetSet(op1: ObjectSetOperation, op2: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }
}