package com.convergencelabs.server.db.data

import com.convergencelabs.server.schema.PolymorphicSerializer
import java.util.TimeZone
import java.text.SimpleDateFormat
import org.json4s.JsonAST.JString
import org.json4s.CustomSerializer
import java.time.Instant
import java.util.Date
import org.json4s.DefaultFormats

object JsonFormats {
  val operationSerializer = PolymorphicSerializer[CreateOperation]("type", Map(
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

    "BooleanSet" -> classOf[CreateBooleanSetOperation]))

  val dataValueSerializer = PolymorphicSerializer[CreateDataValue]("type", Map(
    "object" -> classOf[CreateObjectValue],
    "array" -> classOf[CreateArrayValue],
    "string" -> classOf[CreateStringValue],
    "double" -> classOf[CreateDoubleValue],
    "boolean" -> classOf[CreateBooleanValue],
    "null" -> classOf[CreateNullValue]))

  val UTC = TimeZone.getTimeZone("UTC")
  val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  df.setTimeZone(UTC)

  val instantSerializer = new CustomSerializer[Instant](formats => ({
    case JString(dateString) =>
      // TODO look into Instant.Parse
      val date = df.parse(dateString)
      Instant.ofEpochMilli(date.getTime)
  }, {
    case x: Instant =>
      JString(df.format(Date.from(x)))
  }))
  
  val format = DefaultFormats + operationSerializer + dataValueSerializer + instantSerializer
}