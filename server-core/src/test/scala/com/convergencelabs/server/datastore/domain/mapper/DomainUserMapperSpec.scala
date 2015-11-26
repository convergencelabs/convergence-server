package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.DomainUser
import com.orientechnologies.orient.core.record.impl.ODocument

import DomainUserMapper._

class DomainUserMapperSpec
    extends WordSpec
    with Matchers {

  "An DomainUserMapper" when {
    "when converting a DomainUser" must {
      "correctly map and unmap a DomainUser" in {
        val domainUser = DomainUser(
          "uid",
          "username",
          "firstName",
          "lastName",
          "email")

        val doc = domainUser.asODocument
        val reverted = doc.asDomainUser
        reverted shouldBe domainUser
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asDomainUser
        }
      }
    }
  }
}