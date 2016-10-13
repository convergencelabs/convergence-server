package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.CompoundOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.Operation
import com.convergencelabs.server.domain.model.ot.TransformationFunctionRegistry
import com.convergencelabs.server.domain.model.ReferenceValue

class ReferenceTransformer(private[this] val tfr: TransformationFunctionRegistry) {

  def transform(op: Operation, setReference: ReferenceValue): Option[ReferenceValue] = {
    op match {
      case c: CompoundOperation =>
        transform(c, setReference)
      case d: DiscreteOperation =>
        transform(d, setReference)
    }
  }

  private[this] def transform(op: CompoundOperation, setReference: ReferenceValue): Option[ReferenceValue] = {
    var result: Option[ReferenceValue] = Some(setReference)
    // TODO this could be more efficient if we could break the loop the first time we
    // get none back.
    op.operations.foreach { op =>
      result = result.flatMap { reference => transform(op, reference) }
    }
    result
  }

  private[this] def transform(op: DiscreteOperation, setReference: ReferenceValue): Option[ReferenceValue] = {
    val tf = tfr.getReferenceTransformationFunction(op, setReference.referenceType)
    tf match {
      case Some(tf) => tf.transform(op, setReference)
      case None => throw new IllegalArgumentException(
        s"No reference transformation function found for operation and reference pair (${op.getClass.getName},${setReference.referenceType})")
    }
  }
}
