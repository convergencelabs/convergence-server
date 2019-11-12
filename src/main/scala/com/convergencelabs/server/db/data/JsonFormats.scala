/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.db.data

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.TimeZone

import org.json4s.CustomSerializer
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JDecimal
import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JLong
import org.json4s.JsonAST.JString

import com.convergencelabs.server.util.MappedTypeHits

object JsonFormats {
  val jsonTypeHints = new MappedTypeHits(
    Map(
      "object" -> classOf[CreateObjectValue],
      "array" -> classOf[CreateArrayValue],
      "string" -> classOf[CreateStringValue],
      "double" -> classOf[CreateDoubleValue],
      "boolean" -> classOf[CreateBooleanValue],
      "null" -> classOf[CreateNullValue],
      "date" -> classOf[CreateDateValue],

      "Compound" -> classOf[CreateCompoundOperation],

      "ObjectValue" -> classOf[CreateObjectSetOperation],
      "ObjectAddProperty" -> classOf[CreateObjectAddPropertyOperation],
      "ObjectSetProperty" -> classOf[CreateObjectSetPropertyOperation],
      "ObjectRemoveProperty" -> classOf[CreateObjectRemovePropertyOperation],

      "ArrayInsert" -> classOf[CreateArrayInsertOperation],
      "ArrayRemove" -> classOf[CreateArrayRemoveOperation],
      "ArrayReplace" -> classOf[CreateArrayReplaceOperation],
      "ArrayReorder" -> classOf[CreateArrayReorderOperation],
      "ArraySet" -> classOf[CreateArraySetOperation],

      "StringInsert" -> classOf[CreateStringInsertOperation],
      "StringRemove" -> classOf[CreateStringRemoveOperation],
      "StringSet" -> classOf[CreateStringSetOperation],

      "NumberDelta" -> classOf[CreateNumberDeltaOperation],
      "NumberSet" -> classOf[CreateNumberSetOperation],

      "BooleanSet" -> classOf[CreateBooleanSetOperation],
      
      "DateSet" -> classOf[CreateDateSetOperation]))

  val UTC = TimeZone.getTimeZone("UTC")
  val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  df.setTimeZone(UTC)

  val instantSerializer = new CustomSerializer[Instant](formats => ({
    case JString(dateString) =>
      // TODO look into Instant.Parse
      val date = df.parse(dateString)
      Instant.ofEpochMilli(date.getTime)
    case JInt(millis) =>
      Instant.ofEpochMilli(millis.longValue())
    case JLong(millis) =>
      Instant.ofEpochMilli(millis)
    case JDouble(millis) =>
      Instant.ofEpochMilli(millis.longValue())
    case JDecimal(millis) =>
      Instant.ofEpochMilli(millis.longValue())
  }, {
    case x: Instant =>
      JString(df.format(Date.from(x)))
  }))

  val format = DefaultFormats.withTypeHintFieldName("type") + 
    jsonTypeHints  + 
    instantSerializer 
}