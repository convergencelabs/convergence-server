package com.convergencelabs.server.frontend.rest

import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.jackson.Serialization
import org.json4s.DefaultFormats
import org.json4s.FieldSerializer

trait JsonSupport extends Json4sSupport{
  implicit val serialization = Serialization
  implicit val formats = DefaultFormats + FieldSerializer[ResponseMessage]()
}
