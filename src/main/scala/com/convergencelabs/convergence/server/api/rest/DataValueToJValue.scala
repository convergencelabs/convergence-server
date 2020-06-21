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

package com.convergencelabs.convergence.server.api.rest

import com.convergencelabs.convergence.server.domain.model.data._
import org.json4s.JsonAST._

object DataValueToJValue {
  val ConvergenceTypeFlag = "$$convergence_type"

  val DateTypeValue = "date"

  def toJObject(objectValue: ObjectValue): JObject = {
    toJson(objectValue).asInstanceOf[JObject]
  }

  def toJson(dataValue: DataValue): JValue = {
    dataValue match {
      case NullValue(_) =>
        JNull
      case DoubleValue(_, v) =>
        JDouble(v)
      case BooleanValue(_, v) =>
        JBool(v)
      case StringValue(_, v) =>
        JString(v)
      case ObjectValue(_, v) =>
        val fields = v map { case (k, v) => (k, toJson(v)) }
        JObject(fields.toList)
      case ArrayValue(_, v) =>
        val values = v map (v => toJson(v))
        JArray(values)
      case DateValue(_, v) =>
        JObject(
          ConvergenceTypeFlag -> JString(DateTypeValue),
          DateTypeValue -> JLong(v.toEpochMilli))
    }
  }
}