package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.TokenPublicKey
import com.orientechnologies.orient.core.record.impl.ODocument
import TokenPublicKeyMapper.ODocumentToTokenPublicKey
import TokenPublicKeyMapper.TokenPublicKeyToODocument
import java.time.Instant

class TokenPublicKeyMapperSpec
    extends WordSpec
    with Matchers {

  "An TokenPublicKeyMapper" when {
    "when converting a TokenPublicKey" must {
      "correctly map and unmap a TokenPublicKey" in {
        val publickKey = TokenPublicKey(
          "id",
          "description:",
          Instant.now(),
          "key",
          true)

        val doc = publickKey.asODocument
        val reverted = doc.asTokenPublicKey
        reverted shouldBe publickKey
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asTokenPublicKey
        }
      }
    }
  }
}
