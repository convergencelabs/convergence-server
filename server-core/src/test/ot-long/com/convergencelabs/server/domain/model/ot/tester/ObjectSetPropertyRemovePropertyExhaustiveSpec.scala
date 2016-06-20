package com.convergencelabs.server.domain.model.ot

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewValues
import OperationPairExhaustiveSpec.ValueId

class ObjectSetPropertyRemovePropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetPropertyOperation, ObjectRemovePropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectSetPropertyOperation, ObjectRemovePropertyOperation]] = {
    for {
      setProp <- ExistingProperties
      setValue <- NewValues
      removeProp <- ExistingProperties
    } yield TransformationCase(
      ObjectSetPropertyOperation(ValueId, false, setProp, setValue),
      ObjectRemovePropertyOperation(ValueId, false, removeProp))
  }

  def transform(s: ObjectSetPropertyOperation, c: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetPropertyRemovePropertyTF.transform(s, c)
  }
}
