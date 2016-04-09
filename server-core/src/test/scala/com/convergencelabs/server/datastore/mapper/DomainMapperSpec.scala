package com.convergencelabs.server.datastore.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.record.impl.ODocument

import DomainMapper.DomainUserToODocument
import DomainMapper.ODocumentToDomain

class DomainMapperSpec
    extends WordSpec
    with Matchers {

  "An DomainMapper" when {
    "when converting a Domain" must {
      "correctly map and unmap a Domain" in {
        val domain = Domain(
          "id",
          DomainFqn("ns", "dId"),
          "My Domain",
          "cu0")

        val doc = domain.asODocument
        val owner = new ODocument()
        owner.field("uid", "cu0")
        doc.field("owner", owner)
        val reverted = doc.asDomain
        reverted shouldBe domain
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asDomain
        }
      }
    }
  }
}
