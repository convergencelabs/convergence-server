package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewValues

class ObjectSetPropertySetPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetPropertyOperation, ObjectSetPropertyOperation] {

  val serverOperationType: String = "ObjectSetPropertyOperation"
  val clientOperationType: String = "ObjectSetPropertyOperation"

  def generateCases(): List[TransformationCase[ObjectSetPropertyOperation, ObjectSetPropertyOperation]] = {
    for {
      prop1 <- ExistingProperties
      value1 <- NewValues
      prop2 <- ExistingProperties
      value2 <- NewValues
    } yield TransformationCase(
      ObjectSetPropertyOperation(List(), false, prop1, value1),
      ObjectSetPropertyOperation(List(), false, prop2, value2))
  }

  def transform(s: ObjectSetPropertyOperation, c: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetPropertySetPropertyTF.transform(s, c)
  }
}
