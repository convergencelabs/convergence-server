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

package com.convergencelabs.convergence.server.api.realtime

import org.json4s.JsonAST.JInt
import org.json4s.JsonDSL.{int2jvalue, jobject2assoc, pair2jvalue}
import org.json4s.{DefaultFormats, Extraction, Formats, JNothing, JObject, JValue, Serializer, TypeInfo, jvalue2monadic}
import org.json4s.reflect.Reflector

/**
 * A helper class that serializes polymorphic classes to JSON Values.
 *
 * @param typeField The field to add to the JSON structure to encode he
 *                  type in.
 * @param typeMap   The mapping of codes to concrete classes.
 * @param formats   The json4s formats object to use.
 * @tparam A The super type of the polymorphic class hierarchy o serialize.
 */
private[realtime] class TypeMapSerializer[A: Manifest](typeField: String, typeMap: Map[Int, Class[_ <: A]], formats: Formats = DefaultFormats) extends Serializer[A] {
  private val Class = implicitly[Manifest[A]].runtimeClass
  private val reverseTypeMap = typeMap map (_.swap)

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), A] = {
    case (TypeInfo(Class, _), json) =>
      json \ typeField match {
        case JInt(t) =>
          typeMap.get(t.asInstanceOf[BigInt].intValue) match {
            case Some(tpe) =>
              Extraction.extract(json, Reflector.scalaTypeOf(tpe))(formats).asInstanceOf[A]
            case _ =>
              throw new IllegalArgumentException(s"The serializer does not have a mapping for type: $t")
          }
        case _ =>
          throw new IllegalArgumentException(s"The value to deserialize does not have the typeField: $typeField")
      }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case value: A =>
      reverseTypeMap.get(value.getClass) match {
        case Some(tpe) =>
          val jValue = Extraction.decompose(value)(formats).asInstanceOf[JObject]
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
