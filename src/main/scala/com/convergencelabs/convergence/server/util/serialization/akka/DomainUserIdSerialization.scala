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

package com.convergencelabs.convergence.server.util.serialization.akka

import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{DeserializationContext, JsonSerializer, KeyDeserializer, SerializerProvider}

object DomainUserIdSerialization {

  class MapKeySerializer extends JsonSerializer[DomainUserId] {
    override def serialize(userId: DomainUserId, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
      val userType = userId.userType.toString
      val username = userId.username
      gen.writeFieldName(s"$userType:$username")
    }
  }

  class MapKeyDeserializer extends KeyDeserializer {
    override def deserializeKey(key: String, ctxt: DeserializationContext): DomainUserId = {
      val sepIndex = key.indexOf(':')
      val userType = key.substring(0, sepIndex)
      val username = key.substring(sepIndex + 1)
      DomainUserId(userType, username)
    }
  }
}
