package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewValues
import ObjectOperationExhaustiveSpec.SetObjects

class ObjectSetSetExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetOperation, ObjectSetOperation] {

  val serverOperationType: String = "ObjectSetOperation"
  val clientOperationType: String = "ObjectSetOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ObjectSetOperation, ObjectSetOperation]] = {
    for {
      newObject1 <- SetObjects
      newObject2 <- SetObjects
    } yield TransformationCase(
      ObjectSetOperation(valueId, false, newObject1),
      ObjectSetOperation(valueId, false, newObject2))
  }

  def transform(s: ObjectSetOperation, c: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetSetTF.transform(s, c)
  }
}
