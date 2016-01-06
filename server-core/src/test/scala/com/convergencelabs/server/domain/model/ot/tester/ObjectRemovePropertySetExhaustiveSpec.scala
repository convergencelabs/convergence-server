package com.convergencelabs.server.domain.model.ot

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.SetObjects

class ObjectRemovePropertySetExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectRemovePropertyOperation, ObjectSetOperation] {

  val serverOperationType: String = "ObjectRemovePropertyOperation"
  val clientOperationType: String = "ObjectSetOperation"

  def generateCases(): List[TransformationCase[ObjectRemovePropertyOperation, ObjectSetOperation]] = {
    for {
      newObject <- SetObjects
      removeProp <- ExistingProperties
    } yield TransformationCase(
      ObjectRemovePropertyOperation(List(), false, removeProp),
      ObjectSetOperation(List(), false, newObject))
  }

  def transform(s: ObjectRemovePropertyOperation, c: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectRemovePropertySetTF.transform(s, c)
  }
}
