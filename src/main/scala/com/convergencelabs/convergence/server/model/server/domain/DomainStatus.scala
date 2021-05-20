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

package com.convergencelabs.convergence.server.model.server.domain

import com.fasterxml.jackson.core.`type`.TypeReference

object DomainStatus extends Enumeration {
  type DomainStatus = Value
  val Initializing: DomainStatus = Value("initializing")
  val Error: DomainStatus = Value("error")
  val Online: DomainStatus = Value("online")
  val Offline: DomainStatus = Value("offline")
  val Maintenance: DomainStatus = Value("maintenance")
  val Deleting: DomainStatus = Value("deleting")

  def withLowerCaseName(name: String): Option[DomainStatus.Value] = {
    name match {
      case "initializing" =>
        Some(DomainStatus.Initializing)
      case "error" =>
        Some(DomainStatus.Error)
      case "online" =>
        Some(DomainStatus.Online)
      case "offline" =>
        Some(DomainStatus.Offline)
      case "maintenance" =>
        Some(DomainStatus.Maintenance)
      case "deleting" =>
        Some(DomainStatus.Deleting)
      case _ =>
        None
    }
  }
}

final class DomainStatusTypeReference extends TypeReference[DomainStatus.type]
