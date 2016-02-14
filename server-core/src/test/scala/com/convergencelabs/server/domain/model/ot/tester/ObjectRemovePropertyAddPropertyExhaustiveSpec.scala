package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues

class ObjectRemovePropertyAddPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectRemovePropertyOperation, ObjectAddPropertyOperation] {

  val serverOperationType: String = "ObjectRemovePropertyOperation"
  val clientOperationType: String = "ObjectAddPropertyOperation"

  def generateCases(): List[TransformationCase[ObjectRemovePropertyOperation, ObjectAddPropertyOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      prop2 <- ExistingProperties
    } yield TransformationCase(
      ObjectRemovePropertyOperation(List(), false, prop2),
      ObjectAddPropertyOperation(List(), false, prop1, value1))
  }

  def transform(s: ObjectRemovePropertyOperation, c: ObjectAddPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectRemovePropertyAddPropertyTF.transform(s, c)
  }
}
