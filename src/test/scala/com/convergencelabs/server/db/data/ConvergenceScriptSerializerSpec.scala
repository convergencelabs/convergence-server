/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.db.data

import java.time.Instant

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.DomainUserType

class ConvergenceScriptSerializerSpec extends WordSpecLike with Matchers {

  "A ConvergenceScriptSerializerSpec" when {
    "deserializing a script" must {
      "correctly parse" in {
        val serializer = new ConvergenceScriptSerializer()
        val in = getClass.getResourceAsStream("/com/convergencelabs/server/db/data/convergence-with-domain.yaml")
        val value = serializer.deserialize(in).success.value

        val ConvergenceScript(users, domains) = value

        users.value shouldBe List(
          CreateConvergenceUser("test", SetPassword("plaintext", "password"), "token", "test@example.com", Some("Test"), Some("User"), Some("Test User")))

        val CreateDomain(
          domainId,
          namespace,
          displayName,
          status,
          statusMessage,
          dataImport) = domains.value(0)

        namespace shouldBe "test"
        domainId shouldBe "example"
        displayName shouldBe "Example"
        status shouldBe "online"
        statusMessage shouldBe ""

        val DomainScript(config, jwtAuthKeys, domainUsers, sessions, collections, models) = dataImport.value
        config shouldBe SetDomainConfig(true, CreateJwtKeyPair("Public Key", "Private Key"))

        jwtAuthKeys.value shouldBe List(CreateJwtAuthKey("test-key", Some("a test key"), Instant.parse("2016-11-16T17:49:15.233Z"), "Public Key", true))

        domainUsers.value shouldBe List(
          CreateDomainUser("normal", "test1", Some("Test"), Some("One"), Some("Test One"), Some("test1@example.com"), false, false, None, Some(SetPassword("plaintext", "somePassword"))),
          CreateDomainUser("normal", "test2", Some("Test"), Some("Two"), Some("Test Two"), Some("test2@example.com"), false, false, None, Some(SetPassword("hash", "someHash"))))

        sessions.value shouldBe List(
          CreateDomainSession(
            "84hf",
            "test1",
            DomainUserType.Normal.toString.toLowerCase,
            Instant.parse("2016-11-16T17:49:14.233Z"),
            Some(Instant.parse("2016-11-16T17:49:15.233Z")),
            "password",
            "javascript",
            "1.0",
            "",
            "unknown"))
        
        collections.value shouldBe List(CreateCollection("collection1", "Collection 1", false))

        models.value shouldBe List(
          CreateModel(
            "someId",
            "collection1",
            2,
            Instant.parse("2016-11-16T17:49:15.233Z"),
            Instant.parse("2016-11-16T17:49:15.233Z"),
            CreateObjectValue(
              "vid1",
              Map("myString" -> CreateStringValue("vid2", "my string"))),
            List(
              CreateModelOperation(1L, Instant.parse("2016-11-16T17:49:15.233Z"), "84hf", CreateStringInsertOperation("vid2", false, 0, "!")),
              CreateModelOperation(2L, Instant.parse("2016-11-16T17:49:15.233Z"), "84hf", CreateStringInsertOperation("vid2", false, 1, "@"))),
            List(
              CreateModelSnapshot(
                1L,
                Instant.parse("2016-11-16T17:49:15.233Z"),
                CreateObjectValue(
                  "vid1",
                  Map("myString" -> CreateStringValue("vid2", "my string")))))))

      }
    }
  }
}
