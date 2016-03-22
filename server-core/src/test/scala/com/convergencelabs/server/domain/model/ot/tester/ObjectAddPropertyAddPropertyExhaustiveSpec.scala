package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues

class ObjectAddPropertyAddPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectAddPropertyOperation, ObjectAddPropertyOperation] {

  val serverOperationType: String = "ObjectAddPropertyOperation"
  val clientOperationType: String = "ObjectAddPropertyOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ObjectAddPropertyOperation, ObjectAddPropertyOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      prop2 <- NewProperties
      value2 <- NewValues
    } yield TransformationCase(
      ObjectAddPropertyOperation(valueId, false, prop1, value1),
      ObjectAddPropertyOperation(valueId, false, prop2, value2))
  }

  def transform(s: ObjectAddPropertyOperation, c: ObjectAddPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectAddPropertyAddPropertyTF.transform(s, c)
  }
}
