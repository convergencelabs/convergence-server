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

  private[this] def transformTwoDiscreteOps(s: DiscreteOperation, c: DiscreteOperation): (DiscreteOperation, DiscreteOperation) = {
    if (s.noOp || c.noOp) {
      (s, c)
    } else if (s.id == c.id) {
      transformIdenticalPathOperations(s, c)
    } else {
      (s, c)
    }
  }

  private[this] def transformIdenticalPathOperations(s: DiscreteOperation, c: DiscreteOperation): (DiscreteOperation, DiscreteOperation) = {
    val tf = tfr.getTransformationFunction(s, c)
    tf match {
      case Some(tf) => tf.transform(s, c)
      case None => throw new IllegalArgumentException(
          s"No operation transformation function found for operation pair (${s.getClass.getName},${s.getClass.getName})")
    }
  }
}
