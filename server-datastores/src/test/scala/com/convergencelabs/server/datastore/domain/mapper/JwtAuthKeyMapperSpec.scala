package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.JwtAuthKey
import com.orientechnologies.orient.core.record.impl.ODocument
import JwtAuthKeyMapper.ODocumentToJwtAuthKey
import JwtAuthKeyMapper.JwtAuthKeyToODocument
import java.time.Instant

class JwtAuthKeyMapperSpec
    extends WordSpec
    with Matchers {

  "An JwtAuthKeyMapper" when {
    "when converting a JwtAuthKey" must {
      "correctly map and unmap a JwtAuthKey" in {
        val publickKey = JwtAuthKey(
          "id",
          "description:",
          Instant.now(),
          "key",
          true)

        val doc = publickKey.asODocument("JwtAuthKey")
        val reverted = doc.asJwtAuthKey
        reverted shouldBe publickKey
      }

      "not allow an invalid document class name" in {
        // FIXME replace after upgrade
//        val invalid = new ODocument("SomeClass")
//        intercept[IllegalArgumentException] {
//          invalid.asJwtAuthKey()
//        }
      }
    }
  }
}
