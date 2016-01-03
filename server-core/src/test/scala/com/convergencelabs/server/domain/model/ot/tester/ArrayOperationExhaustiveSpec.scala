package com.convergencelabs.server.domain.model.ot

object ArrayOperationExhaustiveSpec {
  val ArrayLength: Int = 15
}

trait ArrayOperationExhaustiveSpec[S <: ArrayOperation, C <: ArrayOperation] extends OperationPairExhaustiveSpec[MockArrayModel, S, C] {

  def generateIndices(): List[Int] = {
    (0 until ArrayOperationExhaustiveSpec.ArrayLength).toList
  }

  def generateMoveRanges(): List[ArrayMoveRange] = {
    MoveRangeGenerator.createRanges(ArrayOperationExhaustiveSpec.ArrayLength)
  }

  def createMockModel(): MockArrayModel = {
    new MockArrayModel((0 to ArrayOperationExhaustiveSpec.ArrayLength).toList)
  }
}
