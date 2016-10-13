package com.convergencelabs.server.domain.model.ot

import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues
import ObjectOperationExhaustiveSpec.SetObjects
import OperationPairExhaustiveSpec.ValueId

class ObjectSetAddPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetOperation, ObjectAddPropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectSetOperation, ObjectAddPropertyOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      newObject <- SetObjects
    } yield TransformationCase(
      ObjectSetOperation(ValueId, false, newObject),
      ObjectAddPropertyOperation(ValueId, false, prop1, value1))
  }

  def transform(s: ObjectSetOperation, c: ObjectAddPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetAddPropertyTF.transform(s, c)
  }
}
