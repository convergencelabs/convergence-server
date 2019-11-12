/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ReferenceValue
import com.convergencelabs.server.domain.model.ot.{CompoundOperation, DiscreteOperation, Operation, TransformationFunctionRegistry}

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
    //   get none back.
    op.operations.foreach { op =>
      result = result.flatMap { reference => transform(op, reference) }
    }
    result
  }

  private[this] def transform(op: DiscreteOperation, setReference: ReferenceValue): Option[ReferenceValue] = {
    tfr.getReferenceTransformationFunction(op, setReference.referenceType) match {
      case Some(tf) => tf.transform(op, setReference)
      case None => throw new IllegalArgumentException(
        s"No reference transformation function found for operation and reference pair (${op.getClass.getName},${setReference.referenceType})")
    }
  }
}
