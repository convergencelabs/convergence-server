package com.convergencelabs.server.frontend.rest

import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.jackson.Serialization
import org.json4s.DefaultFormats
import org.json4s.FieldSerializer
import java.text.SimpleDateFormat
import java.util.TimeZone
import org.json4s.CustomSerializer
import java.time.Instant
import org.json4s.JsonAST.JString
import java.util.Date
import java.time.Duration
import org.json4s.JsonAST.JLong
import org.json4s.JsonAST.JInt

trait JsonSupport extends Json4sSupport {

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

  implicit val serialization = Serialization

  implicit val formats = DefaultFormats +
    instantSerializer +
    durationSerializer +
    FieldSerializer[ResponseMessage]()
}
