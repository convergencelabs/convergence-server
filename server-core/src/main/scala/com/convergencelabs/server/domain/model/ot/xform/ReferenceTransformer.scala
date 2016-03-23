package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ProcessedOperationEvent
import com.convergencelabs.server.domain.model.ot.Operation
import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.TransformationFunctionRegistry

class ReferenceTransformer(private[this] val tfr: TransformationFunctionRegistry) {
  
  def transform(op: Operation, setReference: SetReference): SetReference = {
    op match {
      case c: CompoundOperation =>
        transform(c, setReference)
      case d: DiscreteOperation =>
        transform(d, setReference)
    }
  }

  private[this] def transform(op: CompoundOperation, setReference: SetReference): SetReference = {
    var result = setReference
    op.operations.foreach { op =>
      result = transform(op, result)
    }
    result
  }
  
  private[this] def transform(op: DiscreteOperation, setReference: SetReference): SetReference = {
    val tf = tfr.getReferenceTransformationFunction(op, setReference.referenceType)
    tf match {
      case Some(tf) => tf.transform(op, setReference)
      case None => throw new IllegalArgumentException(
          s"No reference transformation function found for operation pair (${op.getClass.getName},${setReference.referenceType})")
    }
  }
  
}