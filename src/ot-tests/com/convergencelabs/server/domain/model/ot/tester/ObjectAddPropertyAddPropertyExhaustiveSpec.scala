package com.convergencelabs.convergence.server.domain.model.ot


import ObjectOperationExhaustiveSpec.NewProperties
import ObjectOperationExhaustiveSpec.NewValues
import OperationPairExhaustiveSpec.ValueId

class ObjectAddPropertyAddPropertyExhaustiveSpec extends ObjectOperationExhaustiveSpec[ObjectAddPropertyOperation, ObjectAddPropertyOperation] {

  def generateCases(): List[TransformationCase[ObjectAddPropertyOperation, ObjectAddPropertyOperation]] = {
    for {
      prop1 <- NewProperties
      value1 <- NewValues
      prop2 <- NewProperties
      value2 <- NewValues
    } yield TransformationCase(
      ObjectAddPropertyOperation(ValueId, false, prop1, value1),
      ObjectAddPropertyOperation(ValueId, false, prop2, value2))
  }

  def transform(s: ObjectAddPropertyOperation, c: ObjectAddPropertyOperation): (DiscreteOperation, DiscreteOperation) = {
    ObjectAddPropertyAddPropertyTF.transform(s, c)
  }
}
