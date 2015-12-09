package com.convergencelabs.server.datastore.domain.mapper

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.orientechnologies.orient.core.record.impl.ODocument
import ModelOperationMapper.ModelOperationToODocument
import ModelOperationMapper.ODocumentToModelOperation
import org.json4s.JsonAST.JObject
import com.convergencelabs.server.domain.model.ModelOperation
import java.time.Instant
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ot.StringInsertOperation

class ModelOperationMapperSpec
    extends WordSpec
    with Matchers {

  val OpVersion = 5L

  "An ModelOperationMapper" when {
    "when converting ModelOperation operations" must {
      "correctly map and unmap a ModelOperation" in {

        val modelOperation = ModelOperation(
          ModelFqn("collection", "model"),
          OpVersion,
          Instant.ofEpochMilli(System.currentTimeMillis()),
          "uid",
          "sid",
          StringInsertOperation(List(4), true, 5, "test"))

        val opDoc = modelOperation.asODocument
        val reverted = opDoc.asModelOperation
        reverted shouldBe modelOperation
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asModelOperation
        }
      }
    }
  }
}
