package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.Operation
import com.convergencelabs.server.domain.model.ot.TransformationFunctionRegistry

class ReferenceTransformer(private[this] val tfr: TransformationFunctionRegistry) {

  def transform(op: Operation, setReference: SetReference): Option[SetReference] = {
    op match {
      case c: CompoundOperation =>
        transform(c, setReference)
      case d: DiscreteOperation =>
        transform(d, setReference)
    }
  }

  private[this] def transform(op: CompoundOperation, setReference: SetReference): Option[SetReference] = {
    var result: Option[SetReference] = Some(setReference)
    // TODO this could be more efficient if we could break the loop the first time we
    // get none back.
    op.operations.foreach { op =>
      result = result.flatMap { reference => transform(op, reference) }
    }
    result
  }

  private[this] def transform(op: DiscreteOperation, setReference: SetReference): Option[SetReference] = {
    val tf = tfr.getReferenceTransformationFunction(op, setReference.referenceType)
    tf match {
      case Some(tf) => tf.transform(op, setReference)
      case None => throw new IllegalArgumentException(
        s"No reference transformation function found for operation and reference pair (${op.getClass.getName},${setReference.referenceType})")
    }
  }
}
