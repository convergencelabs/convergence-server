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

import com.google.protobuf.struct.{ListValue, NullValue, Struct, Value}
import grizzled.slf4j.Logging
import org.json4s.JsonAST
import org.json4s.JsonAST._

/**
 * A helper class to convert between json4s and google protocol buffer structs
 * and values.
 */
private[realtime] object JsonProtoConverter extends Logging {

  def toStruct(jsonObject: JObject): Struct = {
    toValue(jsonObject).getStructValue
  }

  def toValue(json: JValue): Value = {
    json match {
      case JsonAST.JNothing =>
        Value().withNullValue(NullValue.NULL_VALUE)

      case JNull =>
        Value().withNullValue(NullValue.NULL_VALUE)
      case JDecimal(num) =>
        Value().withNumberValue(num.doubleValue)
      case JDouble(v) =>
        Value().withNumberValue(v)
      case JLong(v) =>
        Value().withNumberValue(v.doubleValue)
      case JInt(v) =>
        Value().withNumberValue(v.doubleValue)
      case JBool(v) =>
        Value().withBoolValue(v)
      case JString(v) =>
        Value().withStringValue(v)
      case JObject(fields) =>
        val mappedFields = fields.map {
          case (key, value) => (key, toValue(value))
        }
        Value().withStructValue(Struct().addAllFields(mappedFields))
      case JArray(values) =>
        val mappedValues = values.map(toValue)
        Value().withListValue(ListValue(mappedValues))
      case JSet(set) =>
        val mappedValues = set.toList.map(toValue)
        Value().withListValue(ListValue(mappedValues))
    }
  }

  def toJValue(value: Value): JValue = {
    value.kind match {
      case Value.Kind.BoolValue(bool) =>
        JBool(bool)
      case Value.Kind.NumberValue(num) =>
        JDouble(num)
      case Value.Kind.StringValue(str) =>
        JString(str)
      case Value.Kind.NullValue(_) =>
        JNull
      case Value.Kind.StructValue(struct) =>
        val fields = struct.fields.map {
          case (key, value) => (key, toJValue(value))
        }
        JObject(fields.toList)
      case Value.Kind.ListValue(lst) =>
        JArray(lst.values.toList.map(toJValue))
      case Value.Kind.Empty =>
        error(s"Invalid Protocol JSON Value: $value")
        JNull
    }
  }

  def valueMapToJValueMap(map: Map[String, Value]): Map[String, JValue] = {
    map.map {
      case (k, v) => (k, JsonProtoConverter.toJValue(v))
    }
  }

  def jValueMapToValueMap(map: Map[String, JValue]): Map[String, Value] = {
    map.map {
      case (k, v) => (k, JsonProtoConverter.toValue(v))
    }
  }
}