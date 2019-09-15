package com.convergencelabs.server.domain.model.ot


import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues
import ObjectOperationExhaustiveSpec.SetObjects
import OperationPairExhaustiveSpec.ValueId

class ObjectSetRemovePropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetOperation, ObjectRemovePropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectSetOperation, ObjectRemovePropertyOperation]] = {
    for {
      prop1 <- NewProperties
      newObject <- SetObjects
    } yield TransformationCase(
      ObjectSetOperation(ValueId, false, newObject),
      ObjectRemovePropertyOperation(ValueId, false, prop1))
  }

  def transform(s: ObjectSetOperation, c: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetRemovePropertyTF.transform(s, c)
  }
}
