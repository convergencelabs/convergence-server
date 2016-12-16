package com.convergencelabs.server.db.schema

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

class SchemaEqualitySpec
    extends WordSpecLike
    with Matchers {

  var dbCounter = 1
  val dbName = getClass.getSimpleName

  "Schema Equality" when {
    "comparing latest convergence schema" must {
      "return true if schemas are the same" in withDatabases { (db1, db2) =>
        val manifest = DeltaManager.convergenceManifest().get
        val maxVersion = manifest.maxPreReleaseVersion()

        val currentFullDelta = manifest.getFullDelta(maxVersion).get
        val currentDelta = manifest.getIncrementalDelta(maxVersion).get

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1)
        db2.activateOnCurrentThread()
        if (maxVersion > 1) {
          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2)
        }
        DatabaseDeltaProcessor.apply(currentDelta.delta, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
      }
    }
    "comparing pre-release convergence schema" must {
      "return true if schemas are the same" in withDatabases { (db1, db2) =>
        val manifest = DeltaManager.convergenceManifest().get
        val maxVersion = manifest.maxReleasedVersion()

        val currentFullDelta = manifest.getFullDelta(maxVersion).get
        val currentDelta = manifest.getIncrementalDelta(maxVersion).get

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1)
        db2.activateOnCurrentThread()
        if (maxVersion > 1) {
          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2)
        }
        DatabaseDeltaProcessor.apply(currentDelta.delta, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
      }
    }

    "comparing latest domain schema" must {
      "return true if schemas are the same" in withDatabases { (db1, db2) =>
        val manifest = DeltaManager.domainManifest().get
        val maxVersion = manifest.maxPreReleaseVersion()

        val currentFullDelta = manifest.getFullDelta(maxVersion).get
        val currentDelta = manifest.getIncrementalDelta(maxVersion).get

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1)
        db2.activateOnCurrentThread()
        if (maxVersion > 1) {
          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2)
        }
        DatabaseDeltaProcessor.apply(currentDelta.delta, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
      }
    }
    "comparing pre-release domain schema" must {
      "return true if schemas are the same" in withDatabases { (db1, db2) =>
        val manifest = DeltaManager.domainManifest().get
        val maxVersion = manifest.maxReleasedVersion()

        val currentFullDelta = manifest.getFullDelta(maxVersion).get
        val currentDelta = manifest.getIncrementalDelta(maxVersion).get

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1)
        db2.activateOnCurrentThread()
        if (maxVersion > 1) {
          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2)
        }
        DatabaseDeltaProcessor.apply(currentDelta.delta, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
      }
    }
  }

  def withDatabases(testCode: (ODatabaseDocumentTx, ODatabaseDocumentTx) => Any): Unit = {
    val uri1 = s"memory:${dbName}${dbCounter}"
    dbCounter += 1
    val db1 = new ODatabaseDocumentTx(uri1)
    db1.activateOnCurrentThread()
    db1.create()

    val uri2 = s"memory:${dbName}${dbCounter}"
    dbCounter += 1
    val db2 = new ODatabaseDocumentTx(uri2)
    db2.activateOnCurrentThread()
    db2.create()

    testCode(db1, db2)

    db1.activateOnCurrentThread()
    db1.drop()

    db2.activateOnCurrentThread()
    db2.drop()
  }
}
