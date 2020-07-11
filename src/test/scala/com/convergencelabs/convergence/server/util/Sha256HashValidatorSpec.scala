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

package com.convergencelabs.convergence.server.util

import com.convergencelabs.convergence.server.util.Sha256HashValidator.HashValidationFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

final class Sha256HashValidatorSpec extends AnyWordSpecLike with Matchers {

  "Sha256HashValidator" when {
    "validating a hash" must {
      "must validate a correct hash" in {
       val text = "text to calculate the hash for"
        val expected = "eaa4f9e5626ea2034aa249eacdd53cbd82f50f0acb632d8a219abddefbb6277f"
        Sha256HashValidator.validateHash(text, expected) shouldBe Right(())
      }

      "must not validate an incorrect hash" in {
        val text = "incorrect text to calculate the hash for"
        val expected = "eaa4f9e5626ea2034aa249eacdd53cbd82f50f0acb632d8a219abddefbb6277f"
        val actual = "ad307bde54df8d84855cede93784b68486fcf3b7c95581a9df7345580b806f2f"
        Sha256HashValidator.validateHash(text, expected) shouldBe Left(HashValidationFailure(expected, actual))
      }
    }
  }
}


