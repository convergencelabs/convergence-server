package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewValues

class ObjectRemovePropertySetPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectRemovePropertyOperation, ObjectSetPropertyOperation] {

  val serverOperationType: String = "ObjectRemovePropertyOperation"
  val clientOperationType: String = "ObjectSetPropertyOperation"

  def generateCases(): List[TransformationCase[ObjectRemovePropertyOperation, ObjectSetPropertyOperation]] = {
    for {
      setProp <- ExistingProperties
      setValue <- NewValues
      removeProp <- ExistingProperties
    } yield TransformationCase(
      ObjectRemovePropertyOperation(List(), false, removeProp),
      ObjectSetPropertyOperation(List(), false, setProp, setValue))
  }

  def transform(s: ObjectRemovePropertyOperation, c: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectRemovePropertySetPropertyTF.transform(s, c)
  }
}
