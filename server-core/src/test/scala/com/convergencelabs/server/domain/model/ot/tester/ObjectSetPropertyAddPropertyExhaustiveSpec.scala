package com.convergencelabs.server.domain.model.ot


import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues
import OperationPairExhaustiveSpec.ValueId

class ObjectSetPropertyAddPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetPropertyOperation, ObjectAddPropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectSetPropertyOperation, ObjectAddPropertyOperation]] = {
    for {
      setProp <- ExistingProperties
      setValue <- NewValues
      newProp <- NewProperties
      newValue <- NewValues
    } yield TransformationCase(
      ObjectSetPropertyOperation(ValueId, false, setProp, setValue),
      ObjectAddPropertyOperation(ValueId, false, newProp, newValue))
  }

  def transform(s: ObjectSetPropertyOperation, c: ObjectAddPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetPropertyAddPropertyTF.transform(s, c)
  }
}
