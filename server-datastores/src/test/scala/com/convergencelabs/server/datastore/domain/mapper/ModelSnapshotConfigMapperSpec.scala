package com.convergencelabs.server.datastore.domain.mapper

import java.time.Duration
import java.time.temporal.ChronoUnit

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.orientechnologies.orient.core.record.impl.ODocument

import ModelSnapshotConfigMapper.ModelSnapshotConfigToODocument
import ModelSnapshotConfigMapper.ODocumentToModelSnapshotConfig

class ModelSnapshotConfigMapperSpec
    extends WordSpec
    with Matchers {

  "An ModelSnapshotConfigMapper" when {
    "when converting a ModelSnpashotCofig" must {
      "correctly map and unmap a Model" in {
        val modelSnapshotConfig = ModelSnapshotConfig(
          true,
          true,
          true,
          1L,
          2L,
          true,
          true,
          Duration.of(1, ChronoUnit.SECONDS),
          Duration.of(2, ChronoUnit.SECONDS))

        val doc = modelSnapshotConfig.asODocument
        val reverted = doc.asModelSnapshotConfig
        reverted shouldBe modelSnapshotConfig
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asModelSnapshotConfig
        }
      }
    }
  }
}
