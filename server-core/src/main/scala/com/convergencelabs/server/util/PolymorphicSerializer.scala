package com.convergencelabs.server.util

import org.json4s.DefaultFormats
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

/**
 * A custom object serializer that will append a specified field to resulting JObject based
 * on a provided type mapping.  The supplied classes are restricted to being a subclass
 * of the parameterized type T.
 * <p>
 * Example:<pre>
 * sealed trait Person
 * case class Customer(name: String, customerId: String)
 * case class Employee(name: String, employeeId: String)
 *
 * val ser = new PolymorphicSerializer[Person](
 *   "type",
 *   Map("cust" -> classOf[Customer], "emp" -> classOf[Employee])
 * )
 * implicit val formats = DefaultFormats + ser
 * </pre>
 *
 * @constructor Creates a new PolymorphicSerializer from the specified typeField and typeMap
 * @param typeField The string name of the field to add to the object to indicate type.
 * @param typeMap Mapping between subclasses and the string identifier the defines the type.
 */
class PolymorphicSerializer[T: Manifest](typeField: String, typeMap: Map[String, Class[_ <: T]]) extends Serializer[T] {

  // Ensure we don't have any duplicate mappings
  val counts = typeMap.values groupBy (identity(_)) mapValues (_.size)
  counts.find(_._2 > 1) map { clazz =>
    throw new IllegalArgumentException(
      "Mappings need to be unique, but a class was mapped to more than one type id: " + clazz._1)
  }

  private[this] val classMap = typeMap.map(_.swap)
  private[this] val SuperClass = implicitly[Manifest[T]].runtimeClass

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), T] = {
    case (TypeInfo(SuperClass, _), json) => json match {
      case JObject(JField(name, JString(typeId)) :: rest) =>
        typeMap.get(typeId) match {
          case Some(clazz) =>
            Extraction.extract(JObject(rest), TypeInfo(clazz, None)).asInstanceOf[T]
          case None =>
            throw new MappingException(s"No class mapping for subclass of ${SuperClass.getName} with type id ${typeId}.")
        }
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case c: T =>
      classMap.get(c.getClass.asInstanceOf[Class[T]]) match {
        case Some(typeId) =>
          // TODO Need to figure out a way to not have an infinite loop.
          val obj = Extraction.decompose(c)(DefaultFormats).asInstanceOf[JObject]
          val augmented = obj ~ (typeField -> typeId)
          augmented
        case None =>
          throw new MappingException(s"No class mapping type mapping for subclass of ${SuperClass.getName}: ${c.getClass.getName}")
      }
  }
}

/**
 * Companion object to the PolymorphicSerializer adding a convenience apply method
 * so that "new" does not have to be used.  See the PolymorphicSerializer class
 * for details.
 */
object PolymorphicSerializer {
  def apply[T: Manifest](typeField: String, typeMap: Map[String, Class[_ <: T]]) = {
    new PolymorphicSerializer[T](typeField, typeMap)
  }
}

/**
 * A custom object serializer that will append a specified field to resulting JObject based
 * on the simple class name.  The supplied classes are restricted to being a subclass
 * of the parameterized type T. Note, since the simple name is used, all sub classes must
 * have distinct simple names.
 *
 * <p>
 * Example:<pre>
 * sealed trait Person
 * case class Customer(name: String, customerId: String)
 * case class Employee(name: String, employeeId: String)
 *
 * val ser = new SimpleNamePolymorphicSerializer[Person]("type", List[classOf[Customer], classOf[Employee]])
 * implicit val formats = DefaultFormats + ser
 * </pre>
 *
 * @constructor Creates a new PolymorphicSerializer from the specified typeField and class list.
 * @param typeField The string name of the field to add to the object to indicate type.
 * @param classes A list of valid sub classes to process.
 */
class SimpleNamePolymorphicSerializer[T: Manifest](typeField: String, classes: List[Class[_ <: T]]) extends PolymorphicSerializer[T](
  typeField, classes map (c => c.getSimpleName -> c) toMap) {
}

/**
 * Companion object to the SimpleNamePolymorphicSerializer adding a convenience apply method
 * so that "new" does not have to be used.  See the PolymorphicSerializer class
 * for details.
 */
object SimpleNamePolymorphicSerializer {
  def apply[T: Manifest](typeField: String, classes: List[Class[_ <: T]]) = {
    new SimpleNamePolymorphicSerializer[T](typeField, classes)
  }
}