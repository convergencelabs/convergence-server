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

package com.convergencelabs.convergence.server.api

import com.convergencelabs.convergence.server.domain.model.data._
import com.convergencelabs.convergence.server.util.serialization.MappedTypeHints
import org.json4s.{FieldSerializer, JField}

package object realtime {

  private val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("id", x) => Some("i", x)
    case ("value", x) => Some("v", x)
    case ("children", x) => Some("c", x)
  }

  private val deserializer: PartialFunction[JField, JField] = {
    case JField("i", x) => JField("id", x)
    case JField("v", x) => JField("value", x)
    case JField("c", x) => JField("children", x)
  }

  val DataValueFieldSerializer: FieldSerializer[DataValue] = FieldSerializer[DataValue](serializer, deserializer)

  val DataValueTypeHints: MappedTypeHints = MappedTypeHints(Map(
    "0" -> classOf[ObjectValue],
    "1" -> classOf[ArrayValue],
    "2" -> classOf[StringValue],
    "3" -> classOf[DoubleValue],
    "4" -> classOf[BooleanValue],
    "5" -> classOf[NullValue],
    "6" -> classOf[DateValue]))
}
