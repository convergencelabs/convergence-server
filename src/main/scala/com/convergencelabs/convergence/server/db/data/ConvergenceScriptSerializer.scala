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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.json4s.DefaultFormats
import java.io.InputStream
import scala.util.Try
import org.json4s.jackson.JsonMethods
import org.json4s.Extraction
import com.convergencelabs.convergence.server.util.PolymorphicSerializer
import java.util.TimeZone
import java.text.SimpleDateFormat
import org.json4s.JsonAST.JString
import java.time.Instant
import java.util.Date
import org.json4s.CustomSerializer
import java.io.FileNotFoundException

class ConvergenceScriptSerializer {
  
  private[this] val mapper = new ObjectMapper(new YAMLFactory())
  private[this] implicit val format = JsonFormats.format

  def deserialize(in: InputStream): Try[ConvergenceScript] = Try {
    Option(in) map { stream  =>
      val jsonNode = mapper.readTree(stream)
      val jValue = JsonMethods.fromJsonNode(jsonNode)
      Extraction.extract[ConvergenceScript](jValue)
    } getOrElse {
      throw new FileNotFoundException("could not open script file")
    }
  }
}