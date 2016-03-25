package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JValue
import org.scalatest.Finders
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.DataValue

object ArrayOperationExhaustiveSpec {
  val ArrayLength: Int = 15
}

trait ArrayOperationExhaustiveSpec[S <: ArrayOperation, C <: ArrayOperation] extends OperationPairExhaustiveSpec[MockArrayModel, S, C] {

  val value1 = StringValue("vid1", "value1")
  val value2 = StringValue("vid2", "value2")

  def generateIndices(): List[Int] = {
    (0 until ArrayOperationExhaustiveSpec.ArrayLength).toList
  }

  def generateValues(): List[DataValue] = {
    List(value1, value2)
  }

  def generateMoveRanges(): List[ArrayMoveRange] = {
    MoveRangeGenerator.createRanges(ArrayOperationExhaustiveSpec.ArrayLength)
  }

  def createMockModel(): MockArrayModel = {
    new MockArrayModel((0 to ArrayOperationExhaustiveSpec.ArrayLength).toList)
  }
}
