package com.convergencelabs.server.api.rest

import java.time.{Duration, Instant}

import com.convergencelabs.server.domain.model.data.DataValue
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{CustomSerializer, DefaultFormats, FieldSerializer, Formats}
import org.json4s.JsonAST.{JInt, JLong}
import org.json4s.jackson.Serialization

trait JsonSupport extends Json4sSupport {

  val instantSerializer = new CustomSerializer[Instant](formats => ({
    case JInt(num) =>
      Instant.ofEpochMilli(num.longValue())
    case JLong(num) =>
      Instant.ofEpochMilli(num.longValue())
  }, {
    case x: Instant =>
      JLong(x.toEpochMilli)
  }))

  val durationSerializer = new CustomSerializer[Duration](formats => ({
    case JInt(int) =>
      val l = int.longValue()
      Duration.ofMillis(l)
    case JLong(long) =>
      Duration.ofMillis(long)
  }, {
    case x: Duration =>
      JLong(x.toMillis)
  }))

  val dataValueSerializer = new CustomSerializer[DataValue](formats => ({
    case x: Any =>
      ???
  }, {
    case x: DataValue =>
      DataValueToJValue.toJson(x)

  }))

  implicit val serialization: Serialization.type = Serialization

  implicit val formats: Formats = DefaultFormats +
    instantSerializer +
    durationSerializer +
    dataValueSerializer +
    FieldSerializer[ResponseMessage]()
}
