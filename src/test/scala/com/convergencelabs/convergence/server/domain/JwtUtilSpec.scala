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

import java.util.{List => JavaList}

import org.jose4j.jwt.JwtClaims
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

class JwtUtilSpec
    extends AnyWordSpec
    with Matchers {

  "A JwtUtil" when {
    "getting a claim value by type" must {
      "return None if no value is present" in {
        val claims = new JwtClaims()
        claims.setClaim("definedKey", "something")
        JwtUtil.getClaim[String](claims, "not set") shouldBe None
      }

      "return None if the value is the wrong type" in {
        val claims = new JwtClaims()
        val claim = "wrongType"
        claims.setClaim(claim, new Object())
        JwtUtil.getClaim[String](claims, "wrongType") shouldBe None
      }

      "return Some if the value is set and of the correct type" in {
        val claims = new JwtClaims()
        val value = "value"
        val claim = "correctType"
        claims.setClaim(claim, value)
        JwtUtil.getClaim[String](claims, claim).value shouldBe value
      }
    }

    "getting a claim with a list" must {
      "parse the correct java list" in {
        val claims = JwtClaims.parse("{ \"key\": [\"a\", \"b\"] }")
        JwtUtil.getClaim[JavaList[String]](claims, "key").value.asScala.toList shouldBe List("a", "b")
      }
    }
  }

}
