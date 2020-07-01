package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import scala.reflect.ClassTag

object StringOperationExhaustiveSpec {
  val StringModelInitialState: String = "ABCDEFGHIJKLMNO"
}

abstract class StringOperationExhaustiveSpec[S <: StringOperation, C <: StringOperation](implicit s: ClassTag[S], c: ClassTag[C])
    extends OperationPairExhaustiveSpec[MockStringModel, S, C] {

  def generateIndices(): List[Int] = {
    val len = StringOperationExhaustiveSpec.StringModelInitialState.length
    val indices = (0 until len)
    indices.toList
  }

  def generateRemoveRanges(): List[StringDeleteRange] = {
    StringRemoveRangeGenerator.createDeleteRanges(StringOperationExhaustiveSpec.StringModelInitialState)
  }

  def createMockModel(): MockStringModel = {
    new MockStringModel(StringOperationExhaustiveSpec.StringModelInitialState)
  }
}
