/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.api.rest

import com.convergencelabs.server.domain.model.data._
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
          DateTypeValue -> JDouble(v.toEpochMilli()))
    }
  }
}