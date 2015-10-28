package com.convergencelabs.server.datastore.domain

import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JInt

class OrientDBOperationMapperSpec extends WordSpec {

  type JavaMap[K, V] = java.util.HashMap[K, V]

  "An OrientDBOperationMapperSpec" when {
    "asked whether a model exists" must {
      "return false if it doesn't exist" in {

        val value = JObject(
            "array" -> JArray(List(JInt(1), JInt(2), JInt(3))),
            "int" -> JInt(4))

        val op = ArrayInsertOperation(List(2, "foo"), true, 3, value)

        val map = OrientDBOperationMapper.operationToMap(op)

        println(map)
        
        val reverted = OrientDBOperationMapper.mapToOperation(map)
        println(reverted)
      }
    }
  }
}