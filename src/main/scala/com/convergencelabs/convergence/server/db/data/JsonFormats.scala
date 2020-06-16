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

package com.convergencelabs.convergence.server.db.data

import java.text.SimpleDateFormat
import java.time.{Duration, Instant}
import java.util.{Date, TimeZone}

import com.convergencelabs.convergence.server.util.MappedTypeHits
import org.json4s.JsonAST._
import org.json4s.{CustomSerializer, DefaultFormats, Formats}

object JsonFormats {
  private[this] val jsonTypeHints = MappedTypeHits(
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

  private[this] val UTC = TimeZone.getTimeZone("UTC")
  private[this] val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  df.setTimeZone(UTC)

  private[this] val instantSerializer = new CustomSerializer[Instant](formats => ({
    case JString(dateString) =>
      // TODO look into Instant.Parse
      val date = df.parse(dateString)
      Instant.ofEpochMilli(date.getTime)
    case JInt(millis) =>
      Instant.ofEpochMilli(millis.longValue)
    case JLong(millis) =>
      Instant.ofEpochMilli(millis)
    case JDouble(millis) =>
      Instant.ofEpochMilli(millis.longValue)
    case JDecimal(millis) =>
      Instant.ofEpochMilli(millis.longValue)
  }, {
    case x: Instant =>
      JString(df.format(Date.from(x)))
  }))

  private[this] val durationSerializer = new CustomSerializer[Duration](formats => ({
    case JInt(millis) =>
      Duration.ofMillis(millis.longValue)
    case JLong(millis) =>
      Duration.ofMillis(millis)
    case JDouble(millis) =>
      Duration.ofMillis(millis.longValue)
    case JDecimal(millis) =>
      Duration.ofMillis(millis.longValue)
  }, {
    case x: Duration =>
      JLong(x.toMillis)
  }))

  val format: Formats = DefaultFormats.withTypeHintFieldName("type") +
    jsonTypeHints  + 
    instantSerializer +
    durationSerializer
}