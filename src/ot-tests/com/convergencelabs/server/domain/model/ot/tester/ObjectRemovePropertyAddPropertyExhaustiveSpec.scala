package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues

import OperationPairExhaustiveSpec.ValueId

class ObjectRemovePropertyAddPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectRemovePropertyOperation, ObjectAddPropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectRemovePropertyOperation, ObjectAddPropertyOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      prop2 <- ExistingProperties
    } yield TransformationCase(
      ObjectRemovePropertyOperation(ValueId, false, prop2),
      ObjectAddPropertyOperation(ValueId, false, prop1, value1))
  }

  def transform(s: ObjectRemovePropertyOperation, c: ObjectAddPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectRemovePropertyAddPropertyTF.transform(s, c)
  }
}
