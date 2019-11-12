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

package com.convergencelabs.convergence.server.api.rest

import java.time.{Duration, Instant}

import com.convergencelabs.convergence.server.domain.DomainUserType
import com.convergencelabs.convergence.server.domain.DomainUserType.DomainUserType
import com.convergencelabs.convergence.server.domain.model.data.DataValue
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.JsonAST.{JInt, JLong, JString}
import org.json4s.jackson.Serialization
import org.json4s.{CustomSerializer, DefaultFormats, FieldSerializer, Formats}

trait JsonSupport extends Json4sSupport {

  val instantSerializer = new CustomSerializer[Instant](formats => ( {
    case JInt(num) =>
      Instant.ofEpochMilli(num.longValue())
    case JLong(num) =>
      Instant.ofEpochMilli(num.longValue())
  }, {
    case x: Instant =>
      JLong(x.toEpochMilli)
  }))

  val durationSerializer = new CustomSerializer[Duration](formats => ( {
    case JInt(int) =>
      val l = int.longValue()
      Duration.ofMillis(l)
    case JLong(long) =>
      Duration.ofMillis(long)
  }, {
    case x: Duration =>
      JLong(x.toMillis)
  }))

  val dataValueSerializer = new CustomSerializer[DataValue](formats => ( {
    case x: Any =>
      ???
  }, {
    case x: DataValue =>
      DataValueToJValue.toJson(x)

  }))

  val domainUserTypeSerializer = new CustomSerializer[DomainUserType](formats => ( {
    case JString(userType) =>
      DomainUserType.withName(userType)
  }, {
    case domainUserType: DomainUserType =>
      JString(domainUserType.toString)

  }))

  implicit val serialization: Serialization.type = Serialization

  implicit val formats: Formats = DefaultFormats +
    instantSerializer +
    durationSerializer +
    dataValueSerializer +
    domainUserTypeSerializer +
    FieldSerializer[ResponseMessage]()
}
