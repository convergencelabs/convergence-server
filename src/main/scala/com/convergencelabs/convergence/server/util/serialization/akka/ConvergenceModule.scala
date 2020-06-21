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

import com.convergencelabs.convergence.server.BuildInfo
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.fasterxml.jackson.databind.module.SimpleModule
import grizzled.slf4j.Logging
import org.json4s.JsonAST.JValue

class ConvergenceModule extends SimpleModule(BuildInfo.version) with Logging {
  addSerializer(classOf[JValue], new Json4sSerialization.Serializer())
  addDeserializer(classOf[JValue], new Json4sSerialization.Deserializer())

  addKeyDeserializer(classOf[DomainUserId], new DomainUserIdSerialization.MapKeyDeserializer())
  addKeySerializer(classOf[DomainUserId], new DomainUserIdSerialization.MapKeySerializer())
}
