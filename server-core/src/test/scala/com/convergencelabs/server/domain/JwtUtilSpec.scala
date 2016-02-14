package com.convergencelabs.server.domain

import org.jose4j.jwt.JwtClaims
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.WordSpec

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
  }

}
