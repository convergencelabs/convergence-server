package com.convergencelabs.server.db.data

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.json4s.DefaultFormats
import java.io.InputStream
import scala.util.Try
import org.json4s.jackson.JsonMethods
import org.json4s.Extraction
import com.convergencelabs.server.schema.PolymorphicSerializer
import java.util.TimeZone
import java.text.SimpleDateFormat
import org.json4s.JsonAST.JString
import java.time.Instant
import java.util.Date
import org.json4s.CustomSerializer

class DomainScriptSerializer {
  private[this] val mapper = new ObjectMapper(new YAMLFactory())

  implicit val formats = JsonFormats.format

  def deserialize(in: InputStream): Try[DomainScript] = Try {
    val jsonNode = mapper.readTree(in)
    val jValue = JsonMethods.fromJsonNode(jsonNode)
    Extraction.extract[DomainScript](jValue)
  }
}