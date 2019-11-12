/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain

import org.jose4j.jwt.JwtClaims
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.WordSpec

import scala.collection.JavaConverters.asScalaBufferConverter
import java.util.{ List => JavaList }

class JwtUtilSpec
    extends WordSpec
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
