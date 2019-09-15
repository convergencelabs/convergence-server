package com.convergencelabs.server.domain.model.ot

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues
import OperationPairExhaustiveSpec.ValueId

class ObjectAddPropertyRemovePropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectAddPropertyOperation, ObjectRemovePropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectAddPropertyOperation, ObjectRemovePropertyOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      prop2 <- ExistingProperties
    } yield TransformationCase(
      ObjectAddPropertyOperation(ValueId, false, prop1, value1),
      ObjectRemovePropertyOperation(ValueId, false, prop2))
  }

  def transform(s: ObjectAddPropertyOperation, c: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectAddPropertyRemovePropertyTF.transform(s, c)
  }
}
