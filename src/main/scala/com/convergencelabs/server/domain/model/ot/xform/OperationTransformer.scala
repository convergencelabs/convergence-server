package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.Operation
import com.convergencelabs.server.domain.model.ot.ops.CompoundOperation
import com.convergencelabs.server.domain.model.ot.ops.PathComparator
import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.CompoundOperation
import com.convergencelabs.server.domain.model.ot.ops.StringOperation
import com.convergencelabs.server.domain.model.ot.ops.NumberOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayOperation
import com.convergencelabs.server.domain.model.ot.ops.CompoundOperation
import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation

class OperationTransformer(tfr: TransformationFunctionRegistry) {

  def transform(s: Operation, c: Operation): (Operation, Operation) = {
    (s, c) match {
      case (o1: CompoundOperation, o2: Operation) =>
        transformServerCompoundOperation(o1, o2)
      case (o1: Operation, o2: CompoundOperation) =>
        transformClientCompoundOperation(o1, o2)
      case (o1: DiscreteOperation, o2: DiscreteOperation) =>
        transformTwoDiscreteOps(o1, o2)
      case _ => {
        val message = s"Unexpected operation types ${s.getClass.getName} and ${c.getClass.getName}."
        throw new IllegalArgumentException(message)
      }
    }
  }

  private[this] def transformClientCompoundOperation(s: Operation, c: CompoundOperation): (Operation, Operation) = {
    var xFormedS = s
    val newOps = c.operations.map { o =>
      val (o1, o2) = transform(s, o)
      xFormedS = o1
      o2.asInstanceOf[DiscreteOperation]
    }
    (xFormedS, CompoundOperation(newOps))
  }

  private[this] def transformServerCompoundOperation(s: CompoundOperation, c: Operation) = {
    var xFormedC = c
    val newOps = s.operations.map { o =>
      val (o1, o2) = transform(s, o)
      xFormedC = o2
      o1.asInstanceOf[DiscreteOperation]
    }
    (xFormedC, CompoundOperation(newOps))
  }

  private[this] def transformTwoDiscreteOps(s: DiscreteOperation, c: DiscreteOperation): (Operation, Operation) = {
    if (s.noOp || c.noOp) {
      (s, c)
    } else if (PathComparator.areEqual(s.path, c.path)) {
      transformIdenticalPathOperations(s, c)
    } else if (s.isAncestorOf(c)) {
      transformHierarchicalOperations(s, c)
    } else {
      transformHierarchicalOperations(c, s)
    }
  }

  private[this] def transformIdenticalPathOperations(s: DiscreteOperation, c: DiscreteOperation): (Operation, Operation) = {
    val tf = tfr.getTransformationFunction(s, c)
    tf match {
      case Some(tf) => tf.transform(s, c)
      case None => ??? // FIXME put in real exception

    }
  }

  private[this] def transformHierarchicalOperations(a: DiscreteOperation, d: DiscreteOperation): (Operation, Operation) = {
    val ptf = tfr.getPathTransformationFunction(a)
    val result = ptf match {
      case Some(ptf) => ptf.transformDescendantPath(a, d.path)
      case None => ??? // FIXME put in real exception

    }

    result match {
      case NoPathTranslation => (a, d)
      case PathObsoleted => (a, d.clone(noOp = true))
      case PathUpdated(path) => (a, d.clone(path = path))
    }
  }
}