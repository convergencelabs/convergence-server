package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewValues
import ObjectOperationExhaustiveSpec.SetObjects
import OperationPairExhaustiveSpec.ValueId

class ObjectSetSetPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetOperation, ObjectSetPropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectSetOperation, ObjectSetPropertyOperation]] = {
    for {
      prop1 <- ExistingProperties
      value1 <- NewValues
      newObject <- SetObjects
    } yield TransformationCase(
      ObjectSetOperation(ValueId, false, newObject),
      ObjectSetPropertyOperation(ValueId, false, prop1, value1))
  }

  def transform(s: ObjectSetOperation, c: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetSetPropertyTF.transform(s, c)
  }
}
