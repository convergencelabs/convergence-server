package com.convergencelabs.server.datastore.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.record.impl.ODocument

import DomainMapper.DomainUserToODocument
import UserMapper.UserToODocument
import DomainMapper.ODocumentToDomain
import com.convergencelabs.server.domain.DomainStatus
import com.convergencelabs.server.User

class DomainMapperSpec
    extends WordSpec
    with Matchers {

  "An DomainMapper" when {
    "when converting a Domain" must {
      "correctly map and unmap a Domain" in {
        val owner = User("cu0", "test", "test@convergence.com", "test", "test")
        val domain = Domain(
          "id",
          DomainFqn("ns", "dId"),
          "My Domain",
          owner,
          DomainStatus.Online)

        val doc = domain.asODocument
        doc.field("owner", owner.asODocument)
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
