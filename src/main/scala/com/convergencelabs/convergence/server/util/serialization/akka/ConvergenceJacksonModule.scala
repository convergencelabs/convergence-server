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

import com.convergencelabs.convergence.server.domain.DomainUserId
import com.fasterxml.jackson.databind.module.SimpleModule

/**
 * The [[ConvergenceJacksonModule]] adds serialization and deserialization for
 * the types that Convergence serializes through akka using the Jackson CBOR
 * serializer, that can't be handled by automated means.
 */
final class ConvergenceJacksonModule extends SimpleModule() {

  // Handle DomainUserId as a map key.
  addKeyDeserializer(classOf[DomainUserId], new DomainUserIdSerialization.MapKeyDeserializer())
  addKeySerializer(classOf[DomainUserId], new DomainUserIdSerialization.MapKeySerializer())

}
