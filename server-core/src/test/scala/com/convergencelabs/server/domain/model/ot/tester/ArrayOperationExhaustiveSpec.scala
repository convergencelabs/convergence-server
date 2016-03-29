package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JValue
import org.scalatest.Finders
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.DataValue
import scala.reflect.ClassTag

object ArrayOperationExhaustiveSpec {
  val ArrayLength: Int = 15

  val Value1 = StringValue("vid1", "value1")
  val Value2 = StringValue("vid2", "value2")
  val ArrayValue = List(StringValue("vid2", "X"))
}

abstract class ArrayOperationExhaustiveSpec[S <: ArrayOperation, C <: ArrayOperation](implicit s: ClassTag[S], c: ClassTag[C]) extends OperationPairExhaustiveSpec[MockArrayModel, S, C]() {
  import ArrayOperationExhaustiveSpec._

  def generateIndices(): List[Int] = {
    (0 until ArrayLength).toList
  }

  def generateValues(): List[DataValue] = {
    List(Value1, Value2)
  }

  def generateMoveRanges(): List[ArrayMoveRange] = {
    MoveRangeGenerator.createRanges(ArrayLength)
  }

  def createMockModel(): MockArrayModel = {
    new MockArrayModel((0 to ArrayLength).toList)
  }
}
