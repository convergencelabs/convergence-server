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

package com.convergencelabs.convergence.server.db.schema

import com.orientechnologies.orient.core.db.{ODatabaseSession, ODatabaseType, OrientDB, OrientDBConfig}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class SchemaEqualitySpec
  extends AnyWordSpecLike
    with Matchers {

  private[this] var dbCounter = 1
  private[this] val dbName = getClass.getSimpleName

  "Schema Equality" when {
    "comparing pre-released convergence schema" must {
      "return no error if schemas are the same" in withDatabases { (db1, db2) =>
        val manifest = DeltaManager.convergenceManifest().get
        val maxVersion = manifest.maxPreReleaseVersion()

        val currentFullDelta = manifest.getFullDelta(maxVersion).get
        val currentDelta = manifest.getIncrementalDelta(maxVersion).get

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1)
        db2.activateOnCurrentThread()
        if (maxVersion > 1) {
          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2).get
        }
        DatabaseDeltaProcessor.apply(currentDelta.delta, db2).get

        SchemaEqualityTester.assertEqual(db1, db2)
      }
    }
    "comparing released convergence schema" must {
      "return no error if schemas are the same" in withDatabases { (db1, db2) =>
        val manifest = DeltaManager.convergenceManifest().get
        val maxVersion = manifest.maxReleasedVersion()

        val currentFullDelta = manifest.getFullDelta(maxVersion).get
        val currentDelta = manifest.getIncrementalDelta(maxVersion).get

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1).get
        db2.activateOnCurrentThread()
        if (maxVersion > 1) {
          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2).get
        }
        DatabaseDeltaProcessor.apply(currentDelta.delta, db2).get

        SchemaEqualityTester.assertEqual(db1, db2)
      }
    }

    "comparing pre-released domain schema" must {
      "return no error if schemas are the same" in withDatabases { (db1, db2) =>
        val manifest = DeltaManager.domainManifest().get
        val maxVersion = manifest.maxPreReleaseVersion()

        val currentFullDelta = manifest.getFullDelta(maxVersion).get
        val currentDelta = manifest.getIncrementalDelta(maxVersion).get

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1).get
        db2.activateOnCurrentThread()
        if (maxVersion > 1) {
          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2).get
        }
        DatabaseDeltaProcessor.apply(currentDelta.delta, db2).get

        SchemaEqualityTester.assertEqual(db1, db2)
      }
    }
    
    "comparing released domain schema" must {
      "return no error if schemas are the same" in withDatabases { (db1, db2) =>
        val manifest = DeltaManager.domainManifest().get
        val maxVersion = manifest.maxReleasedVersion()

        val currentFullDelta = manifest.getFullDelta(maxVersion).get
        val currentDelta = manifest.getIncrementalDelta(maxVersion).get

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(currentFullDelta.delta, db1).get
        db2.activateOnCurrentThread()
        if (maxVersion > 1) {
          val previousFullDelta = manifest.getFullDelta(maxVersion - 1).get
          DatabaseDeltaProcessor.apply(previousFullDelta.delta, db2).get
        }
        DatabaseDeltaProcessor.apply(currentDelta.delta, db2).get

        SchemaEqualityTester.assertEqual(db1, db2)
      }
    }
  }

  def withDatabases(testCode: (ODatabaseSession, ODatabaseSession) => Any): Unit = {
    val odb = new OrientDB("memory:", "admin", "admin", OrientDBConfig.defaultConfig())    
    val dbName1 = s"$dbName$dbCounter"
    dbCounter += 1
    
    odb.create(dbName1, ODatabaseType.MEMORY)
    val db1 = odb.open(dbName1, "admin", "admin")

    val dbName2 = s"$dbName$dbCounter"
    dbCounter += 1

    odb.create(dbName2, ODatabaseType.MEMORY)
    val db2 = odb.open(dbName2, "admin", "admin")

    testCode(db1, db2)

    db1.activateOnCurrentThread()
    db1.close()
    
    db2.activateOnCurrentThread()
    db2.close()
    
    odb.drop(dbName1)
    odb.drop(dbName2)
    
    odb.close()
  }
}
