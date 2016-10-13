package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.TokenKeyPair
import com.orientechnologies.orient.core.record.impl.ODocument

import TokenKeyPairMapper.ODocumentToTokenKeyPair
import TokenKeyPairMapper.TokenKeyPairToODocument

class TokenKeyPairMapperSpec
    extends WordSpec
    with Matchers {

  "An TokenKeyPairMapper" when {
    "when converting a TokenKeyPair" must {
      "correctly map and unmap a TokenKeyPair" in {
        val pair = TokenKeyPair(
          "public",
          "private")

        val doc = pair.asODocument
        val reverted = doc.asTokenKeyPair
        reverted shouldBe pair
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asTokenKeyPair
        }
      }
    }
  }
}
