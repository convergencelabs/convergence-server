package com.convergencelabs.server.datastore.domain

import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops._
import org.json4s.JsonAST._

class OrientDBOperationMapperSpec extends WordSpec {

  val path = List(3, "foo", 4)
  
  val jsonString = JString("A String")
  val jsonInt = JInt(4)
  
  val complexJsonArray = JArray(List(
    JString("A String"),
    JInt(2),
    JBool(true),
    JNull,
    JDecimal(BigDecimal(5D)),
    JDouble(9D),
    JObject("key" -> JString("value"))
    ))

  val complexJsonObject = JObject(
    "array" -> complexJsonArray,
    "int" -> JInt(4),
    "decimal" -> JDecimal(6D),
    "double" -> JDouble(10D),
    "string" -> JString("another string"),
    "null" -> JNull,
    "boolean" -> JBool(false),
    "object" -> JObject("something" -> JInt(2)))

  "An OrientDBOperationMapper" when {
    "when converting string operations" must {
      "correctly map and unmap an StringInsertOperation" in {
        val op = StringInsertOperation(path, true, 3, "inserted")
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
      
      "correctly map and unmap an StringRemoveOperation" in {
        val op = StringRemoveOperation(path, true, 3, "removed")
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
      
      "correctly map and unmap an StringSetOperation" in {
        val op = StringSetOperation(path, true, "something")
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
    }
    
    "when converting array operations" must {
      "correctly map and unmap an ArrayInsertOperation" in {
        val op = ArrayInsertOperation(path, true, 3, complexJsonObject)
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
      
      "correctly map and unmap an ArrayRemoveOperation" in {
        val op = ArrayRemoveOperation(path, true, 3)
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
      
      "correctly map and unmap an ArrayReplaceOperation" in {
        val op = ArrayReplaceOperation(path, true, 3, complexJsonArray)
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
      
      "correctly map and unmap an ArrayMoveOperation" in {
        val op = ArrayMoveOperation(path, true, 3, 5)
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
      
      "correctly map and unmap an ArraySetOperation" in {
        val op = ArraySetOperation(path, true, complexJsonArray)
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
    }
    
    "when converting object operations" must {
      "correctly map and unmap an ObjectSetPropertyOperation" in {
        val op = ObjectSetPropertyOperation(path, true, "prop", complexJsonObject)
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
      
      "correctly map and unmap an ObjectAddPropertyOperation" in {
        val op = ObjectAddPropertyOperation(path, true, "prop", complexJsonObject)
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
      
      "correctly map and unmap an ObjectRemovePropertyOperation" in {
        val op = ObjectRemovePropertyOperation(path, true, "prop")
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
      
      
      "correctly map and unmap an ObjectSetOperation" in {
        val op = ObjectSetOperation(path, true, complexJsonObject)
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
    }
    
    "when converting number operations" must {
      "correctly map and unmap an NumberAddOperation" in {
        // FIXME
      }
      
      "correctly map and unmap an NumberSetOperation" in {
        // FIXME
      }
      
    }
    
    "when converting compound operations" must {
      "correctly map and unmap a CompoundOperation" in {
        val ops = List(
            ObjectSetOperation(path, true, complexJsonObject),
            ArrayRemoveOperation(path, true, 3))
            
        val op = CompoundOperation(ops)
        val asMap = OrientDBOperationMapper.operationToMap(op)
        val reverted = OrientDBOperationMapper.mapToOperation(asMap)
        assert(op == reverted)
      }
    }      
  }
}