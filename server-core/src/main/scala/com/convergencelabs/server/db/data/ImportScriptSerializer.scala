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
import java.io.FileNotFoundException

class ImportScriptSerializer {
  private[this] val mapper = new ObjectMapper(new YAMLFactory())
  private[this] implicit val format = DefaultFormats

  def deserialize(in: InputStream): Try[ImportScript] = Try {
    Option(in) map { stream  =>
      val jsonNode = mapper.readTree(stream)
      val jValue = JsonMethods.fromJsonNode(jsonNode)
      Extraction.extract[ImportScript](jValue)
    } getOrElse {
      throw new FileNotFoundException("could not open script file")
    }
  }
}