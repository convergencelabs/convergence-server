package com.convergencelabs.server.api.rest

import java.time.Duration
import java.time.Instant

import scala.annotation.implicitNotFound

import org.json4s.CustomSerializer
import org.json4s.DefaultFormats
import org.json4s.FieldSerializer
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JLong
import org.json4s.jackson.Serialization

import com.convergencelabs.server.domain.model.data.DataValue

import de.heikoseeberger.akkahttpjson4s.Json4sSupport

trait JsonSupport extends Json4sSupport {

  val instantSerializer = new CustomSerializer[Instant](formats => ({
    case JInt(num) =>
      Instant.ofEpochMilli(num.longValue())
    case JLong(num) =>
      Instant.ofEpochMilli(num.longValue())
  }, {
    case x: Instant =>
      JLong(x.toEpochMilli())
  }))

  val durationSerializer = new CustomSerializer[Duration](formats => ({
    case JInt(int) =>
      val l = int.longValue()
      Duration.ofMillis(l)
    case JLong(long) =>
      Duration.ofMillis(long)
  }, {
    case x: Duration =>
      JLong(x.toMillis())
  }))

  val dataValueSerializer = new CustomSerializer[DataValue](formats => ({
    case x: Any =>
      ???
  }, {
    case x: DataValue =>
      DataValueToJValue.toJson(x)

  }))

  implicit val serialization = Serialization

  implicit val formats = DefaultFormats +
    instantSerializer +
    durationSerializer +
    dataValueSerializer +
    FieldSerializer[ResponseMessage]()
}
