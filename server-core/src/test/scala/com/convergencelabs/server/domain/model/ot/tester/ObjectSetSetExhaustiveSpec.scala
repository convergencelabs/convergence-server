package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewValues
import ObjectOperationExhaustiveSpec.SetObjects

class ObjectSetSetPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetOperation, ObjectSetPropertyOperation] {

  val serverOperationType: String = "ObjectSetOperation"
  val clientOperationType: String = "ObjectSetPropertyOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ObjectSetOperation, ObjectSetPropertyOperation]] = {
    for {
      prop1 <- ExistingProperties
      value1 <- NewValues
      newObject <- SetObjects
    } yield TransformationCase(
      ObjectSetOperation(valueId, false, newObject),
      ObjectSetPropertyOperation(valueId, false, prop1, value1))
  }

  def transform(s: ObjectSetOperation, c: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetSetPropertyTF.transform(s, c)
  }
}
