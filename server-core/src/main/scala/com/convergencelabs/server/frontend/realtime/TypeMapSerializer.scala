package com.convergencelabs.server.frontend.realtime

import scala.annotation.implicitNotFound

import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.Formats
import org.json4s.JObject
import org.json4s.JString
import org.json4s.JValue
import org.json4s.JsonDSL.jobject2assoc
import org.json4s.JsonDSL.pair2jvalue
import org.json4s.JsonDSL.string2jvalue
import org.json4s.Serializer
import org.json4s.TypeInfo
import org.json4s.jvalue2monadic
import org.json4s.reflect.Reflector

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
