package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewValues
import OperationPairExhaustiveSpec.ValueId

class ObjectSetPropertySetPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetPropertyOperation, ObjectSetPropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectSetPropertyOperation, ObjectSetPropertyOperation]] = {
    for {
      prop1 <- ExistingProperties
      value1 <- NewValues
      prop2 <- ExistingProperties
      value2 <- NewValues
    } yield TransformationCase(
      ObjectSetPropertyOperation(ValueId, false, prop1, value1),
      ObjectSetPropertyOperation(ValueId, false, prop2, value2))
  }

  def transform(s: ObjectSetPropertyOperation, c: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetPropertySetPropertyTF.transform(s, c)
  }
}
