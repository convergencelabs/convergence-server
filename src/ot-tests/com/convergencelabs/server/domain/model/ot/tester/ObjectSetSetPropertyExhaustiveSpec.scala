package com.convergencelabs.convergence.server.domain.model.ot

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewValues
import ObjectOperationExhaustiveSpec.SetObjects
import OperationPairExhaustiveSpec.ValueId

class ObjectSetSetExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetOperation, ObjectSetOperation] {

  def generateCases(): List[TransformationCase[ObjectSetOperation, ObjectSetOperation]] = {
    for {
      newObject1 <- SetObjects
      newObject2 <- SetObjects
    } yield TransformationCase(
      ObjectSetOperation(ValueId, false, newObject1),
      ObjectSetOperation(ValueId, false, newObject2))
  }

  def transform(s: ObjectSetOperation, c: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetSetTF.transform(s, c)
  }
}
