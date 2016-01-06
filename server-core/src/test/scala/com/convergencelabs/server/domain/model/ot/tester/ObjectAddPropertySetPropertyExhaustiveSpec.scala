package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues

class ObjectAddPropertySetPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectAddPropertyOperation, ObjectSetPropertyOperation] {

  val serverOperationType: String = "ObjectAddPropertyOperation"
  val clientOperationType: String = "ObjectSetPropertyOperation"

  def generateCases(): List[TransformationCase[ObjectAddPropertyOperation, ObjectSetPropertyOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      prop2 <- ExistingProperties
      value2 <- NewValues
    } yield TransformationCase(
      ObjectAddPropertyOperation(List(), false, prop1, value1),
      ObjectSetPropertyOperation(List(), false, prop2, value2))
  }

  def transform(s: ObjectAddPropertyOperation, c: ObjectSetPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectAddPropertySetPropertyTF.transform(s, c)
  }
}
