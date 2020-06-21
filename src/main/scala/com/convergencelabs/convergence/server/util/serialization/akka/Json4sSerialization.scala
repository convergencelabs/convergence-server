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

package com.convergencelabs.convergence.server.util.serialization.akka

import com.fasterxml.jackson.core.{JsonGenerator, JsonParser, JsonToken}
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer, JsonSerializer, SerializerProvider}
import org.json4s.JsonAST._
import org.json4s.{JField, JsonAST}

import scala.collection.mutable.ListBuffer

/**
 * This is a helper class to allow Jackson to efficiently serialize and
 * deserialize Json4s content when akka serializes messages over the
 * wire. We use Json4s internally for a native scala api, but akka
 * directly uses jackson.  We ultimately have json4s use jackson
 * but still need this adaptation.
 */
object Json4sSerialization {

  class Serializer extends JsonSerializer[JValue] {
    override def serialize(value: JValue, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
      value match {
        case JsonAST.JNothing =>
        // no-op
        case JsonAST.JNull =>
          gen.writeNull()
        case JsonAST.JString(s) =>
          gen.writeString(s)
        case JsonAST.JDouble(num) =>
          gen.writeNumber(num)
        case JsonAST.JDecimal(num) =>
          gen.writeNumber(num.bigDecimal)
        case JsonAST.JLong(num) =>
          gen.writeNumber(num)
        case JsonAST.JInt(num) =>
          gen.writeNumber(num.intValue)
        case JsonAST.JBool(value) =>
          gen.writeBoolean(value)
        case JsonAST.JObject(obj) =>
          gen.writeStartObject()
          obj.toMap.foreach { case (str, value) =>
            gen.writeFieldName(str)
            serialize(value, gen, serializers)
          }
          gen.writeEndObject()
        case JsonAST.JArray(arr) =>
          gen.writeStartArray()
          arr.foreach(value => serialize(value, gen, serializers))
          gen.writeEndArray()
        case JsonAST.JSet(set) =>
          gen.writeStartArray()
          set.foreach(value => serialize(value, gen, serializers))
          gen.writeEndArray()
      }
    }
  }

  class Deserializer extends JsonDeserializer[JValue] {
    def deserialize(jp: JsonParser, ctxt: DeserializationContext): JValue = {
      processValue(jp)
    }
  }

  def processValue(jp: JsonParser): JValue = {
    val token = jp.getCurrentToken

    if (token == JsonToken.START_OBJECT) {
      processObject(jp)
    } else if (token == JsonToken.START_ARRAY) {
      processArray(jp)
    } else if (token == JsonToken.VALUE_TRUE) {
      JBool.True
    } else if (token == JsonToken.VALUE_FALSE) {
      JBool.False
    } else if (token == JsonToken.VALUE_STRING) {
      val str = jp.getText()
      JString(str)
    } else if (token == JsonToken.VALUE_NULL) {
      JNull
    } else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
      val num = jp.getDecimalValue
      JDecimal(num)
    } else if (token == JsonToken.VALUE_NUMBER_INT) {
      val num = jp.getLongValue
      JLong(num)
    } else {
      JNothing
    }
  }

  def processObject(jp: JsonParser): JObject = {
    var token = jp.nextValue()
    val fields = ListBuffer[JField]()
    while (token != JsonToken.END_OBJECT) {
      val field = jp.getCurrentName
      val value = processValue(jp)
      fields += (field -> value)
      token = jp.nextValue()
    }

    JObject(fields.toList)
  }

  def processArray(jp: JsonParser): JArray = {
    var token = jp.nextValue()
    val arr = ListBuffer[JValue]()
    while (token != JsonToken.END_ARRAY) {
      val value = processValue(jp)
      arr += value
      token = jp.nextValue()
    }

    JArray(arr.toList)
  }
}


