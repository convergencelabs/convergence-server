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

package com.convergencelabs.convergence.server.domain

import org.scalatest.Finders
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.wordspec.AnyWordSpec

class DomainStatusSpec
    extends AnyWordSpec
    with Matchers {

  "A DomainStatus" when {
    "Getting a value from a string" must {
      "return Initializing when the string is 'initializing'" in {
        DomainStatus.withName("initializing") shouldBe DomainStatus.Initializing
      }
      
      "return Online when the string is 'online'" in {
        DomainStatus.withName("online") shouldBe DomainStatus.Online
      }
      
      "return Offline when the string is 'offline'" in {
        DomainStatus.withName("offline") shouldBe DomainStatus.Offline
      }
      
      "return Error when the string is 'error'" in {
        DomainStatus.withName("error") shouldBe DomainStatus.Error
      }
      
      "return Maintenance when the string is 'maintenance'" in {
        DomainStatus.withName("maintenance") shouldBe DomainStatus.Maintenance
      }
      
      "return Terminiating when the string is 'terminating'" in {
        DomainStatus.withName("deleting") shouldBe DomainStatus.Deleting
      }
    }
    
    "calling toString" must {
      "return terminiating when the string is 'Terminating'" in {
        DomainStatus.Deleting.toString() shouldBe "deleting"
      }
    }
  }
}
