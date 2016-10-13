package com.convergencelabs.server.datastore.domain.mapper

import java.time.Instant
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelSnapshot
import com.convergencelabs.server.domain.model.ModelSnapshotMetaData
import com.orientechnologies.orient.core.record.impl.ODocument
import ModelSnapshotMapper.ModelSnapshotToODocument
import ModelSnapshotMapper.ODocumentToModelSnapshot
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue

class ModelSnapshotMapperSpec
    extends WordSpec
    with Matchers {

  val SnapshotVersion = 4L

  "An ModelSnapshotMapper" when {
    "when converting Model operations" must {
      "correctly map and unmap a Model" in {
        val modelSnapshot = ModelSnapshot(
          ModelSnapshotMetaData(
            ModelFqn("collection", "model"),
            SnapshotVersion,
            Instant.ofEpochMilli(System.currentTimeMillis())),
          ObjectValue("vid1", Map("foo" -> StringValue("vid2", "test"))))

        val doc = modelSnapshot.asODocument
        val reverted = doc.asModelSnapshot
        reverted shouldBe modelSnapshot
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asModelSnapshot
        }
      }
    }
  }
}
