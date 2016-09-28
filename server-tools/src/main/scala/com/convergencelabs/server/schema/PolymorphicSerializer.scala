package com.convergencelabs.server.schema

import scala.annotation.implicitNotFound
import scala.language.existentials
import scala.language.postfixOps

import org.json4s.Extraction
import org.json4s.Formats
import org.json4s.JField
import org.json4s.JValue
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.JsonDSL.jobject2assoc
import org.json4s.JsonDSL.pair2jvalue
import org.json4s.JsonDSL.string2jvalue
import org.json4s.MappingException
import org.json4s.Serializer
import org.json4s.TypeInfo

class PolymorphicSerializer[T: Manifest](typeField: String, typeMap: Map[String, Class[_]]) extends Serializer[T] {
  val classMap = typeMap.map(_.swap)
  if (classMap.size != typeMap.size) {
    // TODO tell them which one violated it.
    throw new IllegalArgumentException("Mappings need to be unique")
  }

  val Class = implicitly[Manifest[T]].runtimeClass

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), T] = {
    case (TypeInfo(Class, _), json) => json match {
      case JObject(JField(name, JString(typeId)) :: rest) =>
        typeMap.get(typeId) match {
          case Some(clazz) =>
            Extraction.extract(JObject(rest), TypeInfo(clazz, None)).asInstanceOf[T]
          case None =>
            throw new MappingException(s"No class mapping for subclass of ${Class.getName} with type id ${typeId}.")
        }
      case _ =>
        throw new MappingException(s"Subclass of ${Class.getName} must have a type field of '${typeField}'.")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case c: Change =>
      classMap.get(c.getClass) match {
        case Some(typeId) =>
          val obj = Extraction.decompose(c).asInstanceOf[JObject]
          val augmented = obj ~ (typeField -> typeId)
          augmented
        case None =>
          throw new MappingException(s"No class mapping type mapping for subclass of ${Class.getName}: ${c.getClass.getName}")
      }
    case x: Any =>
      throw new MappingException(s"Can't map a class that is not a subclass of ${Class.getName}: ${x.getClass.getName}")
  }
}

class SimpleNamePolymorphicSerializer[T: Manifest](typeField: String, classes: List[Class[_]]) extends PolymorphicSerializer[T](
    typeField, classes map (c => c.getSimpleName -> c) toMap) {
}