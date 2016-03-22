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
import org.json4s.JsonAST.JDouble

// scalastyle:off magic.number
class OperationMapperSpec extends WordSpec with Matchers {

  val X = "X"

  val Id = "testId"
  val NoOp = true
  val JVal = JDouble(5)
  val Prop = "prop"

  val operations = List(
    ObjectSetPropertyOperation(Id, NoOp, Prop, JVal),
    ObjectAddPropertyOperation(Id, NoOp, Prop, JVal),
    ObjectRemovePropertyOperation(Id, NoOp, Prop),
    ObjectSetOperation(Id, NoOp, JObject(List("p" -> JVal))),

    ArrayInsertOperation(Id, NoOp, 1, JVal),
    ArrayRemoveOperation(Id, NoOp, 1),
    ArrayReplaceOperation(Id, NoOp, 1, JVal),
    ArrayMoveOperation(Id, NoOp, 1, 2),
    ArraySetOperation(Id, NoOp, JArray(List(JVal))),

    StringInsertOperation(Id, NoOp, 1, X),
    StringRemoveOperation(Id, NoOp, 1, X),
    StringSetOperation(Id, NoOp, X),

    NumberSetOperation(Id, NoOp, JVal),
    NumberAddOperation(Id, NoOp, JVal),

    BooleanSetOperation(Id, NoOp, true),

    CompoundOperation(List(NumberSetOperation(Id, NoOp, JVal))))

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
