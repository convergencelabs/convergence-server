package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation

object ArrayOperationTransformer {
  def transformInsertInsert(op1: ArrayInsertOperation, op2: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformInsertRemove(op1: ArrayInsertOperation, op2: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformInsertReplace(op1: ArrayInsertOperation, op2: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformInsertMove(op1: ArrayInsertOperation, op2: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformInsertSet(op1: ArrayInsertOperation, op2: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformRemoveInsert(op1: ArrayRemoveOperation, op2: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformRemoveRemove(op1: ArrayRemoveOperation, op2: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformRemoveReplace(op1: ArrayRemoveOperation, op2: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformRemoveMove(op1: ArrayRemoveOperation, op2: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformRemoveSet(op1: ArrayRemoveOperation, op2: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformReplaceInsert(op1: ArrayReplaceOperation, op2: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformReplaceRemove(op1: ArrayReplaceOperation, op2: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformReplaceReplace(op1: ArrayReplaceOperation, op2: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformReplaceMove(op1: ArrayReplaceOperation, op2: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformReplaceSet(op1: ArrayReplaceOperation, op2: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformMoveInsert(op1: ArrayMoveOperation, op2: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformMoveRemove(op1: ArrayMoveOperation, op2: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformMoveReplace(op1: ArrayMoveOperation, op2: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformMoveMove(op1: ArrayMoveOperation, op2: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformMoveSet(op1: ArrayMoveOperation, op2: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetInsert(op1: ArraySetOperation, op2: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetRemove(op1: ArraySetOperation, op2: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetReplace(op1: ArraySetOperation, op2: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetMove(op1: ArraySetOperation, op2: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetSet(op1: ArraySetOperation, op2: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

}