package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues
import ObjectOperationExhaustiveSpec.SetObjects

class ObjectSetRemovePropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetOperation, ObjectRemovePropertyOperation] {

  val serverOperationType: String = "ObjectSetOperation"
  val clientOperationType: String = "ObjectRemovePropertyOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ObjectSetOperation, ObjectRemovePropertyOperation]] = {
    for {
      prop1 <- NewProperties
      newObject <- SetObjects
    } yield TransformationCase(
      ObjectSetOperation(valueId, false, newObject),
      ObjectRemovePropertyOperation(valueId, false, prop1))
  }

  def transform(s: ObjectSetOperation, c: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetRemovePropertyTF.transform(s, c)
  }
}
