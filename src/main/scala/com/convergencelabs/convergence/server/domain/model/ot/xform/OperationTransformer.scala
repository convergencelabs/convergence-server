/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.model.ot

private[model] class OperationTransformer(
    private[this] val tfr: TransformationFunctionRegistry) {

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
    tfr.getOperationTransformationFunction(s, c) match {
      case Some(tf) => tf.transform(s, c)
      case None => throw new IllegalArgumentException(
          s"No operation transformation function found for operation pair (${s.getClass.getName},${s.getClass.getName})")
    }
  }
}
