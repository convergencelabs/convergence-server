package com.convergencelabs.convergence.server.domain.model.ot

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.SetObjects
import OperationPairExhaustiveSpec.ValueId

class ObjectRemovePropertySetExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectRemovePropertyOperation, ObjectSetOperation] {

  def generateCases(): List[TransformationCase[ObjectRemovePropertyOperation, ObjectSetOperation]] = {
    for {
      newObject <- SetObjects
      removeProp <- ExistingProperties
    } yield TransformationCase(
      ObjectRemovePropertyOperation(ValueId, false, removeProp),
      ObjectSetOperation(ValueId, false, newObject))
  }

  def transform(s: ObjectRemovePropertyOperation, c: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectRemovePropertySetTF.transform(s, c)
  }
}
