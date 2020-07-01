package com.convergencelabs.convergence.server.backend.services.domain.model.ot


import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewValues
import OperationPairExhaustiveSpec.ValueId

class ObjectRemovePropertySetPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectRemovePropertyOperation, ObjectSetPropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectRemovePropertyOperation, ObjectSetPropertyOperation]] = {
    for {
      setProp <- ExistingProperties
      setValue <- NewValues
      removeProp <- ExistingProperties
    } yield TransformationCase(
      ObjectRemovePropertyOperation(ValueId, false, removeProp),
      ObjectSetPropertyOperation(ValueId, false, setProp, setValue))
  }

  def transform(s: ObjectRemovePropertyOperation, c: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectRemovePropertySetPropertyTF.transform(s, c)
  }
}
