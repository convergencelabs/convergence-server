package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonDSL.int2jvalue

import ObjectOperationExhaustiveSpec.ExistingProperties
import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues

class ObjectSetPropertyAddPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectSetPropertyOperation, ObjectAddPropertyOperation] {

  val serverOperationType: String = "ObjectSetPropertyOperation"
  val clientOperationType: String = "ObjectAddPropertyOperation"

  def generateCases(): List[TransformationCase[ObjectSetPropertyOperation, ObjectAddPropertyOperation]] = {
    for {
      setProp <- ExistingProperties
      setValue <- NewValues
      newProp <- NewProperties
      newValue <- NewValues
    } yield TransformationCase(
      ObjectSetPropertyOperation(List(), false, setProp, setValue),
      ObjectAddPropertyOperation(List(), false, newProp, newValue))
  }

  def transform(s: ObjectSetPropertyOperation, c: ObjectAddPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectSetPropertyAddPropertyTF.transform(s, c)
  }
}
