package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewValues

class ObjectSetPropertyRemovePropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetPropertyOperation, ObjectRemovePropertyOperation] {

  val serverOperationType: String = "ObjectSetPropertyOperation"
  val clientOperationType: String = "ObjectRemovePropertyOperation"

  def generateCases(): List[TransformationCase[ObjectSetPropertyOperation, ObjectRemovePropertyOperation]] = {
    for {
      setProp <- ExistingProperties
      setValue <- NewValues
      removeProp <- ExistingProperties
    } yield TransformationCase(
      ObjectSetPropertyOperation(List(), false, setProp, setValue),
      ObjectRemovePropertyOperation(List(), false, removeProp))
  }

  def transform(s: ObjectSetPropertyOperation, c: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetPropertyRemovePropertyTF.transform(s, c)
  }
}
