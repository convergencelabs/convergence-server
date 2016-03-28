package com.convergencelabs.server.domain.model.ot


import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues
import ObjectOperationExhaustiveSpec.SetObjects
import OperationPairExhaustiveSpec.ValueId

class ObjectAddPropertySetExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectAddPropertyOperation, ObjectSetOperation] {

  def generateCases(): List[TransformationCase[ObjectAddPropertyOperation, ObjectSetOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      newObject <- SetObjects
    } yield TransformationCase(
      ObjectAddPropertyOperation(ValueId, false, prop1, value1),
      ObjectSetOperation(ValueId, false, newObject))
  }

  def transform(s: ObjectAddPropertyOperation, c: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectAddPropertySetTF.transform(s, c)
  }
}
