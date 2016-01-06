package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt

import org.json4s.JObject
import org.json4s.JsonAST.JInt
import org.json4s.JsonDSL.int2jvalue
import org.json4s.JsonDSL.pair2jvalue

import ObjectOperationExhaustiveSpec.InitialState

// scalastyle:off magic.number
object ObjectOperationExhaustiveSpec {
  val InitialState: JObject = JObject(List(("A" -> JInt(1))))

  val SetObject1: JObject = ("X" -> 24)
  val SetObject2: JObject = ("Y" -> 25)

  val SetObjects = List(SetObject1, SetObject2)

  val ExistingProperties = List("A", "B", "C")
  val NewProperties = List("D", "E", "F")
  val NewValues = List(4, 5, 6)
}

trait ObjectOperationExhaustiveSpec[S <: ObjectOperation, C <: ObjectOperation] extends OperationPairExhaustiveSpec[MockObjectModel, S, C] {
  def createMockModel(): MockObjectModel = {
    new MockObjectModel(InitialState.values)
  }
}
