/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.model.server.domain

import com.fasterxml.jackson.core.`type`.TypeReference

import scala.util.Try

/**
 * The availability of the domain controls if the domain is
 * accessible or not.
 */
object DomainAvailability extends Enumeration {
  val Online: Value = Value("online")
  val Offline: Value = Value("offline")
  val Maintenance: Value = Value("maintenance")

  def withNameOpt(name: String): Option[DomainAvailability.Value] = {
    Try(DomainAvailability.withName(name)).map(Some(_)).getOrElse(None)
  }
}

final class DomainAvailabilityTypeReference extends TypeReference[DomainAvailability.type]
