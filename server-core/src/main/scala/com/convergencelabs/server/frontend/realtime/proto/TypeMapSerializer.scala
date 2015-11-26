package com.convergencelabs.server.frontend.realtime.proto

import org.json4s._
import org.json4s.reflect.Reflector
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

class TypeMapSerializer[A: Manifest](typeField: String, typeMap: Map[String, Class[_ <: A]]) extends Serializer[A] {
    val Class = implicitly[Manifest[A]].runtimeClass
    private val reverseTypeMap = typeMap map (_.swap)

    def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), A] = {
      case (TypeInfo(Class, _), json) => {
        val JString(t) = json \\ typeField
        Extraction.extract(json, Reflector.scalaTypeOf(typeMap(t)))((DefaultFormats)).asInstanceOf[A]
      }
    }

    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case op: A => {
        val t = reverseTypeMap(op.getClass)
        
        val json = Extraction.decompose(op)(DefaultFormats).asInstanceOf[JObject]
        json ~ (typeField -> t)
      }
    }
  }