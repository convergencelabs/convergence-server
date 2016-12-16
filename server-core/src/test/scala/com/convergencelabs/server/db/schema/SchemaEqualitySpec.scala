package com.convergencelabs.server.db.schema

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import org.scalatest.BeforeAndAfterEach

class SchemaEqualitySpec extends WordSpecLike with Matchers with BeforeAndAfterEach {

  var dbCounter = 1
  val dbName = getClass.getSimpleName

  var db1: ODatabaseDocumentTx = null
  var db2: ODatabaseDocumentTx = null

  var processor1: DatabaseDeltaProcessor = null
  var processor2: DatabaseDeltaProcessor = null

  override def beforeEach() {
    val uri1 = s"memory:${dbName}${dbCounter}"
    dbCounter += 1
    db1 = new ODatabaseDocumentTx(uri1)
    db1.activateOnCurrentThread()
    db1.create()

    val uri2 = s"memory:${dbName}${dbCounter}"
    dbCounter += 1
    db2 = new ODatabaseDocumentTx(uri2)
    db2.activateOnCurrentThread()
    db2.create()
  }

  override def afterEach() {
    db1.activateOnCurrentThread()
    db1.drop()
    db1 = null

    db2.activateOnCurrentThread()
    db2.drop()
    db2 = null
  }

  "Schema Equality" when {
//    "comparing latest convergence schema" must {
//      "return true if schemas are the same" in {
//        val manifest = DeltaManager.convergenceManifest().get
//        val maxVersion = manifest.maxPreReleaseVersion()
//
//        val currentFullDelta = manifest.getFullDelta(maxVersion).get
//        val currentDelta = manifest.getIncrementalDelta(maxVersion).get
//
//        db1.activateOnCurrentThread()
//        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1)
//        db2.activateOnCurrentThread()
//        if (maxVersion > 1) {
//          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
//          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2)
//        }
//        DatabaseDeltaProcessor.apply(currentDelta.delta, db2)
//
//        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
//      }
//    }
//    "comparing pre-release convergence schema" must {
//      "return true if schemas are the same" in {
//        val manifest = DeltaManager.convergenceManifest().get
//        val maxVersion = manifest.maxReleasedVersion()
//
//        val currentFullDelta = manifest.getFullDelta(maxVersion).get
//        val currentDelta = manifest.getIncrementalDelta(maxVersion).get
//
//        db1.activateOnCurrentThread()
//        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1)
//        db2.activateOnCurrentThread()
//        if (maxVersion > 1) {
//          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
//          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2)
//        }
//        DatabaseDeltaProcessor.apply(currentDelta.delta, db2)
//
//        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
//      }
//    }
//
//    "comparing latest domain schema" must {
//      "return true if schemas are the same" in {
//        val manifest = DeltaManager.domainManifest().get
//        val maxVersion = manifest.maxPreReleaseVersion()
//
//        val currentFullDelta = manifest.getFullDelta(maxVersion).get
//        val currentDelta = manifest.getIncrementalDelta(maxVersion).get
//
//        db1.activateOnCurrentThread()
//        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1)
//        db2.activateOnCurrentThread()
//        if (maxVersion > 1) {
//          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
//          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2)
//        }
//        DatabaseDeltaProcessor.apply(currentDelta.delta, db2)
//
//        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
//      }
//    }
//    "comparing pre-release domain schema" must {
//      "return true if schemas are the same" in {
//        val manifest = DeltaManager.domainManifest().get
//        val maxVersion = manifest.maxReleasedVersion()
//
//        val currentFullDelta = manifest.getFullDelta(maxVersion).get
//        val currentDelta = manifest.getIncrementalDelta(maxVersion).get
//
//        db1.activateOnCurrentThread()
//        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1)
//        db2.activateOnCurrentThread()
//        if (maxVersion > 1) {
//          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
//          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2)
//        }
//        DatabaseDeltaProcessor.apply(currentDelta.delta, db2)
//
//        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
//      }
//    }
  }
}
