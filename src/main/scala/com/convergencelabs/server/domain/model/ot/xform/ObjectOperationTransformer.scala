package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation

object ObjectOperationTransformer {

  // Add Property
  def transformAddPropertyAddProperty(op1: ObjectAddPropertyOperation, op2: ObjectAddPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  def transformAddPropertySetProperty(op1: ObjectAddPropertyOperation, op2: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  def transformAddPropertyRemoveProperty(op1: ObjectAddPropertyOperation, op2: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  def transformAddPropertySet(op1: ObjectAddPropertyOperation, op2: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }
  
  
  // Set Property
  def transformSetPropertyAddProperty(op1: ObjectSetPropertyOperation, op2: ObjectAddPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  def transformSetPropertySetProperty(op1: ObjectSetPropertyOperation, op2: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  def transformSetPropertyRemoveProperty(op1: ObjectSetPropertyOperation, op2: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  def transformSetPropertySet(op1: ObjectSetPropertyOperation, op2: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  // Remove Property
  def transformRemovePropertyAddProperty(op1: ObjectRemovePropertyOperation, op2: ObjectAddPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }
  
  def transformRemovePropertySetProperty(op1: ObjectRemovePropertyOperation, op2: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  def transformRemovePropertyRemoveProperty(op1: ObjectRemovePropertyOperation, op2: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  def transformRemovePropertySet(op1: ObjectRemovePropertyOperation, op2: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  // Set
  def transformSetAddProperty(op1: ObjectSetOperation, op2: ObjectAddPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  def transformSetSetProperty(op1: ObjectSetOperation, op2: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  def transformSetRemoveProperty(op1: ObjectSetOperation, op2: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }

  def transformSetSet(op1: ObjectSetOperation, op2: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    (op1, op2)
  }
}