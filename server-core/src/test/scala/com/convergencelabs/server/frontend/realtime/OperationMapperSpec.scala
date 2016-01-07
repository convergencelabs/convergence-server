package com.convergencelabs.server.frontend.realtime

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JObject
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation

// scalastyle:off magic.number
class OperationMapperSpec extends WordSpec with Matchers {

  val X = "X"

  val Path = List("1", 2, "3")
  val NoOp = true
  val JVal = JInt(5)
  val Prop = "prop"

  val operations = List(
    ObjectSetPropertyOperation(Path, NoOp, Prop, JVal),
    ObjectAddPropertyOperation(Path, NoOp, Prop, JVal),
    ObjectRemovePropertyOperation(Path, NoOp, Prop),
    ObjectSetOperation(Path, NoOp, JObject(List("p" -> JVal))),

    ArrayInsertOperation(Path, NoOp, 1, JVal),
    ArrayRemoveOperation(Path, NoOp, 1),
    ArrayReplaceOperation(Path, NoOp, 1, JVal),
    ArrayMoveOperation(Path, NoOp, 1, 2),
    ArraySetOperation(Path, NoOp, JArray(List(JVal))),

    StringInsertOperation(Path, NoOp, 1, X),
    StringRemoveOperation(Path, NoOp, 1, X),
    StringSetOperation(Path, NoOp, X),

    NumberSetOperation(Path, NoOp, JVal),
    NumberAddOperation(Path, NoOp, JVal),

    BooleanSetOperation(Path, NoOp, true),

    CompoundOperation(List(NumberSetOperation(Path, NoOp, JVal))))

  "An OperationMapper" when {
    "mapping an unmapping operations" must {
      "correctly map and unmap operations" in {
        operations.foreach { op =>
          val data = OperationMapper.mapOutgoing(op)
          val reverted = OperationMapper.mapIncoming(data)
          reverted shouldBe op
        }
      }
    }
  }
}
