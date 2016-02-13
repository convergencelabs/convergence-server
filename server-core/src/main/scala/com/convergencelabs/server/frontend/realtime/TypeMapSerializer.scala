package com.convergencelabs.server.frontend.realtime

import scala.annotation.implicitNotFound
import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.Formats
import org.json4s.JNothing
import org.json4s.JObject
import org.json4s.JString
import org.json4s.JValue
import org.json4s.JsonDSL.jobject2assoc
import org.json4s.JsonDSL.pair2jvalue
import org.json4s.JsonDSL.int2jvalue
import org.json4s.Serializer
import org.json4s.TypeInfo
import org.json4s.jvalue2monadic
import org.json4s.reflect.Reflector
import org.json4s.JsonAST.JInt

class TypeMapSerializer[A: Manifest](typeField: String, typeMap: Map[Int, Class[_ <: A]]) extends Serializer[A] {
  val Class = implicitly[Manifest[A]].runtimeClass
  private val reverseTypeMap = typeMap map (_.swap)

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), A] = {
    case (TypeInfo(Class, _), json) => {
      json \ typeField match {
        case JInt(t) =>
          typeMap.get(t.asInstanceOf[Int]) match {
            case Some(tpe) =>
              Extraction.extract(json, Reflector.scalaTypeOf(tpe))((DefaultFormats)).asInstanceOf[A]
            case _ =>
              throw new IllegalArgumentException(s"The serializer does not have a mapping for type: $t")
          }
        case _ =>
          throw new IllegalArgumentException(s"The value to deserialize does not have the typeField ${typeField}")
      }
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case value: A => {
      reverseTypeMap.get(value.getClass) match {
        case Some(tpe) =>
          val jValue = Extraction.decompose(value)(DefaultFormats).asInstanceOf[JObject]
          jValue \ typeField match {
            case JNothing =>
              jValue ~ (typeField -> tpe)
            case _ =>
              throw new IllegalArgumentException(
                  s"the supplied value to serialize already has the field '$typeField' which conflicts with the type field")
          }
        case None =>
          throw new IllegalArgumentException(s"No type mapping for class: ${value.getClass}")
      }
    }
  }
}
