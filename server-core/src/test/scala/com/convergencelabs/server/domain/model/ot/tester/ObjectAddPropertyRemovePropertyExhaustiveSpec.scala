package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues

class ObjectAddPropertyRemovePropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectAddPropertyOperation, ObjectRemovePropertyOperation] {

  val serverOperationType: String = "ObjectAddPropertyOperation"
  val clientOperationType: String = "ObjectRemovePropertyOperation"

  def generateCases(): List[TransformationCase[ObjectAddPropertyOperation, ObjectRemovePropertyOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      prop2 <- ExistingProperties
    } yield TransformationCase(
      ObjectAddPropertyOperation(List(), false, prop1, value1),
      ObjectRemovePropertyOperation(List(), false, prop2))
  }

  def transform(s: ObjectAddPropertyOperation, c: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectAddPropertyRemovePropertyTF.transform(s, c)
  }
}
