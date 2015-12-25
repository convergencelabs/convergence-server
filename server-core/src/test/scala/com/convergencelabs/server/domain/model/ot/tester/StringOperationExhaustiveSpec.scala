package com.convergencelabs.server.domain.model.ot

object StringOperationExhaustiveSpec {
  val StringModelInitialState: String = "ABCDEFGHIJKLMNO"
}

trait StringOperationExhaustiveSpec[S <: StringOperation, C <: StringOperation] extends OperationPairExhaustiveSpec[MockStringModel, S, C] {
  
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
