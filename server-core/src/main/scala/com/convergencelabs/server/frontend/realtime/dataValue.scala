package com.convergencelabs.server.frontend.realtime

import org.json4s.FieldSerializer
import org.json4s.JField
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue
import org.json4s.DefaultFormats

package object data {

  val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("id", x) => Some("i", x)
    case ("value", x) => Some("v", x)
    case ("children", x) => Some("c", x)
  }

  val deserializer: PartialFunction[JField, JField] = {
    case JField("i", x) => JField("id", x)
    case JField("v", x) => JField("value", x)
    case JField("c", x) => JField("children", x)
  }

  val DataValueFieldSerializer = FieldSerializer[DataValue](serializer, deserializer)

  val DataValueTypeHints = MappedTypeHits(Map(
    "0" -> classOf[ObjectValue],
    "1" -> classOf[ArrayValue],
    "2" -> classOf[StringValue],
    "3" -> classOf[DoubleValue],
    "4" -> classOf[BooleanValue],
    "5" -> classOf[NullValue]))
}
