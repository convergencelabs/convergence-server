package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues
import ObjectOperationExhaustiveSpec.SetObjects

class ObjectAddPropertySetExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectAddPropertyOperation, ObjectSetOperation] {

  val serverOperationType: String = "ObjectAddPropertyOperation"
  val clientOperationType: String = "ObjectSetOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ObjectAddPropertyOperation, ObjectSetOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      newObject <- SetObjects
    } yield TransformationCase(
      ObjectAddPropertyOperation(valueId, false, prop1, value1),
      ObjectSetOperation(valueId, false, newObject))
  }

  def transform(s: ObjectAddPropertyOperation, c: ObjectSetOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectAddPropertySetTF.transform(s, c)
  }
}
