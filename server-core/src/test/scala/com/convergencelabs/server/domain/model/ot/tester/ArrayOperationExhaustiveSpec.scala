package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JValue
import org.scalatest.Finders

object ArrayOperationExhaustiveSpec {
  val ArrayLength: Int = 15
}

trait ArrayOperationExhaustiveSpec[S <: ArrayOperation, C <: ArrayOperation] extends OperationPairExhaustiveSpec[MockArrayModel, S, C] {

  def generateIndices(): List[Int] = {
    (0 until ArrayOperationExhaustiveSpec.ArrayLength).toList
  }

  def generateValues(): List[JValue] = {
    List(JInt(1), JInt(2))
  }

  def generateMoveRanges(): List[ArrayMoveRange] = {
    MoveRangeGenerator.createRanges(ArrayOperationExhaustiveSpec.ArrayLength)
  }

  def createMockModel(): MockArrayModel = {
    new MockArrayModel((0 to ArrayOperationExhaustiveSpec.ArrayLength).toList)
  }
}
