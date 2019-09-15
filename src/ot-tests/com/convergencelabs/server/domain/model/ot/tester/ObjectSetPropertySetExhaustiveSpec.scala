package com.convergencelabs.server.domain.model.ot

import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues
import ObjectOperationExhaustiveSpec.SetObjects
import OperationPairExhaustiveSpec.ValueId

class ObjectSetPropertySetExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetPropertyOperation, ObjectSetOperation] {

  def generateCases(): List[TransformationCase[ObjectSetPropertyOperation, ObjectSetOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      newObject <- SetObjects
    } yield TransformationCase(
      ObjectSetPropertyOperation(ValueId, false, prop1, value1),
      ObjectSetOperation(ValueId, false, newObject))
  }

  def transform(s: ObjectSetPropertyOperation, c: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetPropertySetTF.transform(s, c)
  }
}
