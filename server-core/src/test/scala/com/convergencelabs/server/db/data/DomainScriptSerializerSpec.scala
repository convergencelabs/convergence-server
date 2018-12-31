package com.convergencelabs.server.db.data

import java.time.Instant

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

class DomainScriptSerializerSpec extends WordSpecLike with Matchers {

  "A CreateDomainScriptSerializer" when {
    "deserializing a scropt" must {
      "corrrectly parse" in {
        val serializer = new DomainScriptSerializer()
        val in = getClass.getResourceAsStream("/com/convergencelabs/server/db/data/import-domain-test.yaml")
        val value = serializer.deserialize(in).success.value

        val DomainScript(config, jwtAuthKeys, users, sessions, collections, models) = value
        config shouldBe SetDomainConfig(true, CreateJwtKeyPair("Public Key", "Private Key"))

        jwtAuthKeys.value shouldBe List(CreateJwtAuthKey("test-key", Some("a test key"), Instant.parse("2016-11-16T17:49:15.233Z"), "Public Key", true))

        users.value shouldBe List(
          CreateDomainUser("normal", "test1", Some("Test"), Some("One"), Some("Test One"), Some("test1@example.com"), Some(SetPassword("plaintext", "somePassword"))),
          CreateDomainUser("normal", "test2", Some("Test"), Some("Two"), Some("Test Two"), Some("test2@example.com"), Some(SetPassword("hash", "someHash"))))

        sessions.value shouldBe List(
          CreateDomainSession(
            "84hf",
            "test1",
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
