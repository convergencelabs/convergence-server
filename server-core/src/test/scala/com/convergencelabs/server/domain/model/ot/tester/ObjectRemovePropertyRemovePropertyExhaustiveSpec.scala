package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues

class ObjectRemovePropertyRemovePropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectRemovePropertyOperation, ObjectRemovePropertyOperation] {

  val serverOperationType: String = "ObjectRemovePropertyOperation"
  val clientOperationType: String = "ObjectRemovePropertyOperation"

  def generateCases(): List[TransformationCase[ObjectRemovePropertyOperation, ObjectRemovePropertyOperation]] = {
    for {
      prop1 <- ExistingProperties
      prop2 <- ExistingProperties
    } yield TransformationCase(
      ObjectRemovePropertyOperation(List(), false, prop1),
      ObjectRemovePropertyOperation(List(), false, prop2))
  }

  def transform(s: ObjectRemovePropertyOperation, c: ObjectRemovePropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectRemovePropertyRemovePropertyTF.transform(s, c)
  }
}
