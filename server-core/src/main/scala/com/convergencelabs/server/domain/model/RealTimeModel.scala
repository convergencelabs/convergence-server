package com.convergencelabs.server.domain.model

import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JBool
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JNull

class RealTimeModel(private val obj: JObject) {

  val valuesById = scala.collection.mutable.Map[String, RealTimeValue]()
  
  val data = this.createValue(None, None, obj)
  
  def createValue(
    parent: Option[RealTimeContainerValue],
    parentField: Option[Any],
    value: JValue): RealTimeValue = {
    value match {
      case v: JString => new RealTimeString(this, parent, parentField, v)
      case v: JDouble => new RealTimeDouble(this, parent, parentField, v)
      case v: JBool => new RealTimeBoolean(this, parent, parentField, v)
      case v: JObject => new RealTimeObject(this, parent, parentField, v)
      case v: JArray => new RealTimeArray(this, parent, parentField, v)
      case JNull => new RealTimeNull(this, parent, parentField) 
      case _ => throw new IllegalArgumentException("Unsupported type")
    }
  }
}