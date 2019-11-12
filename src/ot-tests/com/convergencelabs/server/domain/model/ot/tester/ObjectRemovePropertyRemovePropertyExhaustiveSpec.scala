package com.convergencelabs.convergence.server.domain.model.ot

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues
import OperationPairExhaustiveSpec.ValueId

class ObjectRemovePropertyRemovePropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectRemovePropertyOperation, ObjectRemovePropertyOperation] {

  val valueId = "testId"

  def generateCases(): List[TransformationCase[ObjectRemovePropertyOperation, ObjectRemovePropertyOperation]] = {
    for {
      prop1 <- ExistingProperties
      prop2 <- ExistingProperties
    } yield TransformationCase(
      ObjectRemovePropertyOperation(ValueId, false, prop1),
      ObjectRemovePropertyOperation(ValueId, false, prop2))
  }

  def transform(s: ObjectRemovePropertyOperation, c: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectRemovePropertyRemovePropertyTF.transform(s, c)
  }
}
