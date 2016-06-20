package com.convergencelabs.server.domain.model.ot

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues

import OperationPairExhaustiveSpec.ValueId

class ObjectAddPropertySetPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectAddPropertyOperation, ObjectSetPropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectAddPropertyOperation, ObjectSetPropertyOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      prop2 <- ExistingProperties
      value2 <- NewValues
    } yield TransformationCase(
      ObjectAddPropertyOperation(ValueId, false, prop1, value1),
      ObjectSetPropertyOperation(ValueId, false, prop2, value2))
  }

  def transform(s: ObjectAddPropertyOperation, c: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectAddPropertySetPropertyTF.transform(s, c)
  }
}
