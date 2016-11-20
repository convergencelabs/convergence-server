package com.convergencelabs.server.db.data

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

class ImportScriptSerializerSpec extends WordSpecLike with Matchers {

  "A ImportScriptSerializerSpec" when {
    "deserializing a scropt" must {
      "corrrectly parse" in {
        val serializer = new ImportScriptSerializer()
        val in = getClass.getResourceAsStream("/com/convergencelabs/server/db/data/convergence-script.yaml")
        val value = serializer.deserialize(in).success.value

        val ImportScript(users, domains) = value

        users.value shouldBe List(
          CreateConvergenceUser("test1", "myPassword", "test1@example.com", Some("Test"), Some("One"), Some("Test One")))

        domains.value shouldBe List(CreateDomain(
          "test1",
          "code-editor",
          "Code Editor",
          "online",
          "",
          "test1",
          "fooDatabase",
          "abc",
          "password",
          "xyz",
          "password"))
      }
    }
  }
}
