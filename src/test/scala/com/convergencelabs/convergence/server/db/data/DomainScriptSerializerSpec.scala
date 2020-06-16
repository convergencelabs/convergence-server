/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.db.data

import java.time.{Duration, Instant}

import com.convergencelabs.convergence.server.datastore.domain.CollectionPermissions
import com.convergencelabs.convergence.server.domain.{DomainUserType, ModelSnapshotConfig}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class DomainScriptSerializerSpec extends AnyWordSpecLike with Matchers {

  "A CreateDomainScriptSerializer" when {
    "deserializing a script" must {
      "correctly parse" in {
        val serializer = new DomainScriptSerializer()
        val in = getClass.getResourceAsStream("/com/convergencelabs/convergence/server/db/data/import-domain-test.yaml")
        val value = serializer.deserialize(in).success.value

        val DomainScript(config, jwtAuthKeys, users, sessions, collections, models) = value
        config shouldBe SetDomainConfig(anonymousAuth = true, CreateJwtKeyPair("Public Key", "Private Key"))

        jwtAuthKeys.value shouldBe List(CreateJwtAuthKey("test-key", Some("a test key"), Instant.parse("2016-11-16T17:49:15.233Z"), "Public Key", enabled = true))

        users.value shouldBe List(
          CreateDomainUser("normal", "test1", Some("Test"), Some("One"), Some("Test One"), Some("test1@example.com"), None, disabled = false, deleted = false, None, Some(SetPassword("plaintext", "somePassword"))),
          CreateDomainUser("normal", "test2", Some("Test"), Some("Two"), Some("Test Two"), Some("test2@example.com"), None, disabled = false, deleted = false, None, Some(SetPassword("hash", "someHash"))))

        sessions.value shouldBe List(
          CreateDomainSession(
            "84hf",
            "test1",
            DomainUserType.Normal.toString,
            Instant.parse("2016-11-16T17:49:14.233Z"),
            Some(Instant.parse("2016-11-16T17:49:15.233Z")),
            "password",
            "javascript",
            "1.0",
            "",
            "unknown"))

        collections.value shouldBe List(
          CreateCollection("collection1", "Collection 1", overrideSnapshotConfig = false,
            ModelSnapshotConfig(
              snapshotsEnabled = false,
              triggerByVersion = false,
              limitedByVersion = false,
              1000,
              1000,
              triggerByTime = false,
              limitedByTime = false,
              Duration.ofMillis(600000),
              Duration.ofMillis(600000)),
            CollectionPermissions(create = true, read = true, write = true, remove = true, manage = true)))

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
              CreateModelOperation(1L, Instant.parse("2016-11-16T17:49:15.233Z"), "84hf", CreateStringInsertOperation("vid2", noOp = false, 0, "!")),
              CreateModelOperation(2L, Instant.parse("2016-11-16T17:49:15.233Z"), "84hf", CreateStringInsertOperation("vid2", noOp = false, 1, "@"))),
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
