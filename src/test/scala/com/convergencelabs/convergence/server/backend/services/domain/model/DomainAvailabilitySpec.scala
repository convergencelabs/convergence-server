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

package com.convergencelabs.convergence.server.backend.services.domain.model

import com.convergencelabs.convergence.server.model.server.domain.{DomainAvailability, DomainStatus}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DomainAvailabilitySpec
    extends AnyWordSpec
    with Matchers {

  "A DomainAvailability" when {
    "Getting a value from a string" must {
      "return Online when the string is 'online'" in {
        DomainAvailability.withName("online") shouldBe DomainAvailability.Online
      }

      "return Offline when the string is 'offline'" in {
        DomainAvailability.withName("offline") shouldBe DomainAvailability.Offline
      }

      "return Maintenance when the string is 'maintenance'" in {
        DomainAvailability.withName("maintenance") shouldBe DomainAvailability.Maintenance
      }
    }
    
    "calling toString" must {
      "return 'online' when the value is Online" in {
        DomainAvailability.Online.toString shouldBe "online"
      }

      "return 'offline' when the value is Offline" in {
        DomainAvailability.Offline.toString shouldBe "offline"
      }

      "return 'maintenance' when the value is Maintenance" in {
        DomainAvailability.Maintenance.toString shouldBe "maintenance"
      }
    }
  }
}
