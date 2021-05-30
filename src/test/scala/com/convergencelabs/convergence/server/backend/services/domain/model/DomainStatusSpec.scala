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

package com.convergencelabs.convergence.server.backend.services.domain.model

import com.convergencelabs.convergence.server.model.server.domain.DomainStatus
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DomainStatusSpec
    extends AnyWordSpec
    with Matchers {

  "A DomainStatus" when {
    "Getting a value from a string" must {
      "return Initializing when the string is 'initializing'" in {
        DomainStatus.withName("initializing") shouldBe DomainStatus.Initializing
      }
      
      "return Error when the string is 'error'" in {
        DomainStatus.withName("error") shouldBe DomainStatus.Error
      }
      
      "return Deleting when the string is 'terminating'" in {
        DomainStatus.withName("deleting") shouldBe DomainStatus.Deleting
      }
    }
    
    "calling toString" must {
      "return 'deleting' for a Deleting value" in {
        DomainStatus.Deleting.toString shouldBe "deleting"
      }
    }
  }
}
