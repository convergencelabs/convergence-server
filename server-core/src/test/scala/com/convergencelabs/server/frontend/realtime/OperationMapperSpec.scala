package com.convergencelabs.server.frontend.realtime

import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JInt
import com.convergencelabs.server.domain.model.ot._
import org.json4s.JsonAST.JArray

class OperationMapperSpec extends WordSpec with Matchers {
  
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
     ArraySetOperation(Path, NoOp, JArray(List(JVal))),
     
     StringInsertOperation(Path, NoOp, 1, "x"),
     StringRemoveOperation(Path, NoOp, 1, "x"),
     StringSetOperation(Path, NoOp, "x"),
     
     NumberSetOperation(Path, NoOp, JVal),
     NumberAddOperation(Path, NoOp, JVal),
     
     BooleanSetOperation(Path, NoOp, true)
  )
  
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