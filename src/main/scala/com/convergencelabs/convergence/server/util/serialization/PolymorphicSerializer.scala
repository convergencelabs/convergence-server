/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.util.serialization

import org.json4s.JsonAST.{JObject, JString}
import org.json4s.JsonDSL.{jobject2assoc, pair2jvalue, string2jvalue}
import org.json4s.{DefaultFormats, Extraction, Formats, JField, JValue, MappingException, Serializer, TypeInfo}

import scala.language.postfixOps

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
 * "type",
 * Map("cust" -> classOf[Customer], "emp" -> classOf[Employee])
 * )
 * implicit val formats = DefaultFormats + ser
 * </pre>
 *
 * @constructor Creates a new PolymorphicSerializer from the specified typeField and typeMap
 * @param typeField The string name of the field to add to the object to indicate type.
 * @param typeMap   Mapping between subclasses and the string identifier the defines the type.
 */
class PolymorphicSerializer[T: Manifest](typeField: String, typeMap: Map[String, Class[_ <: T]]) extends Serializer[T] {

  // Ensure we don't have any duplicate mappings
  private[this] val counts = typeMap.values.groupBy(identity).view.mapValues(_.size).toMap
  counts.find(_._2 > 1) map { clazz =>
    throw new IllegalArgumentException(
      "Mappings need to be unique, but a class was mapped to more than one type id: " + clazz._1)
  }

  private[this] val classMap = typeMap.map(_.swap)
  private[this] val SuperClass = implicitly[Manifest[T]].runtimeClass

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), T] = {
    case (TypeInfo(SuperClass, _), json) => json match {
      case JObject(JField(_, JString(typeId)) :: rest) =>
        typeMap.get(typeId) match {
          case Some(clazz) =>
            Extraction.extract(JObject(rest), TypeInfo(clazz, None)).asInstanceOf[T]
          case None =>
            throw new MappingException(s"No class mapping for subclass of ${SuperClass.getName} with type id $typeId.")
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
  def apply[T: Manifest](typeField: String, typeMap: Map[String, Class[_ <: T]]): PolymorphicSerializer[T] = {
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
 * @param classes   A list of valid sub classes to process.
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
  def apply[T: Manifest](typeField: String, classes: List[Class[_ <: T]]): SimpleNamePolymorphicSerializer[T] = {
    new SimpleNamePolymorphicSerializer[T](typeField, classes)
  }
}
