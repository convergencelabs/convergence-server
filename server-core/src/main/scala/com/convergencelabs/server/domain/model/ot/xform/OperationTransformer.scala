package com.convergencelabs.server.domain.model.ot

private[model] class OperationTransformer(tfr: TransformationFunctionRegistry) {

  def transform(s: Operation, c: Operation): (Operation, Operation) = {
    (s, c) match {
      case (o1: CompoundOperation, o2: Operation) =>
        transformServerCompoundOperation(o1, o2)
      case (o1: Operation, o2: CompoundOperation) =>
        transformClientCompoundOperation(o1, o2)
      case (o1: DiscreteOperation, o2: DiscreteOperation) =>
        transformTwoDiscreteOps(o1, o2)
    }
  }

  private[this] def transformClientCompoundOperation(s: Operation, c: CompoundOperation): (Operation, Operation) = {
    var xFormedS = s
    val newOps = c.operations.map { o =>
      val (o1, o2) = transform(xFormedS, o)
      xFormedS = o1
      o2.asInstanceOf[DiscreteOperation]
    }
    (xFormedS, CompoundOperation(newOps))
  }

  private[this] def transformServerCompoundOperation(s: CompoundOperation, c: Operation) = {
    var xFormedC = c
    val newOps = s.operations.map { o =>
      val (o1, o2) = transform(o, xFormedC)
      xFormedC = o2
      o1.asInstanceOf[DiscreteOperation]
    }
    (CompoundOperation(newOps), xFormedC)
  }

  private[this] def transformTwoDiscreteOps(s: DiscreteOperation, c: DiscreteOperation): (Operation, Operation) = {
    if (s.noOp || c.noOp) {
      (s, c)
    } else if (PathComparator.areEqual(s.path, c.path)) {
      transformIdenticalPathOperations(s, c)
    } else if (PathComparator.isAncestorOf(s.path, c.path)) {
      transformHierarchicalOperations(s, c)
    } else if (PathComparator.isAncestorOf(c.path, s.path)) {
      transformHierarchicalOperations(c, s).swap
    } else {
      (s, c)
    }
  }

  private[this] def transformIdenticalPathOperations(s: DiscreteOperation, c: DiscreteOperation): (Operation, Operation) = {
    val tf = tfr.getTransformationFunction(s, c)
    tf match {
      case Some(tf) => tf.transform(s, c)
      case None => throw new IllegalArgumentException(
          s"No operation transformation function found for operation pair (${s.getClass.getName},${s.getClass.getName})")
    }
  }

  private[this] def transformHierarchicalOperations(a: DiscreteOperation, d: DiscreteOperation): (Operation, Operation) = {
    val ptf = tfr.getPathTransformationFunction(a)
    val result = ptf match {
      case Some(ptf) => ptf.transformDescendantPath(a, d.path)
      case None => throw new IllegalArgumentException(s"No path transformation function found for ancestor operation: ${a.getClass.getName}")
    }

    result match {
      case NoPathTransformation => (a, d)
      case PathObsoleted => (a, d.clone(noOp = true))
      case PathUpdated(path) => (a, d.clone(path = path))
    }
  }
}
