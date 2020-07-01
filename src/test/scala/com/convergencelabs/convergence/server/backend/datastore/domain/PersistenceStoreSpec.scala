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

package com.convergencelabs.convergence.server.backend.datastore.domain

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import com.convergencelabs.convergence.server.backend.db.schema.{DeltaCategory, TestingSchemaManager}
import com.convergencelabs.convergence.server.backend.db.{ConnectedSingleDatabaseProvider, DatabaseProvider}
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.db.{ODatabaseType, OrientDB, OrientDBConfig}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object PersistenceStoreSpec {
  val OrientDBAdmin = "admin"
}

abstract class PersistenceStoreSpec[S](category: DeltaCategory.Value)
  extends AnyWordSpec
  with Matchers
  with BeforeAndAfterAll {
  
  import PersistenceStoreSpec._
  
  OLogManager.instance().setConsoleLevel("WARNING")

  private[this] val dbCounter = new AtomicInteger(1)
  private[this] val orientDB: OrientDB = new OrientDB(s"memory:target/orientdb/PersistenceStoreSpec/${getClass.getSimpleName}", OrientDBConfig.defaultConfig)

  override protected def afterAll(): Unit = {
    orientDB.close()
  }
  
  def withPersistenceStore(testCode: S => Any): Unit = {
    // make sure no accidental collisions
    val dbName = s"${getClass.getSimpleName}/${nextDbId()}"

    orientDB.create(dbName, ODatabaseType.MEMORY)
    val db = orientDB.open(dbName, OrientDBAdmin, OrientDBAdmin)
    val dbProvider = new ConnectedSingleDatabaseProvider(db)

    try {
      dbProvider.connect().get
      val mgr = new TestingSchemaManager(db, category, true)
      mgr.install().get
      val store = createStore(dbProvider)
      testCode(store)
    } finally {
      dbProvider.shutdown()
      orientDB.drop(dbName)
    }
  }

  private[this] def nextDbId(): Int = {
    dbCounter.getAndIncrement()
  }

  protected def truncatedInstantNow(): Instant = {
    java.util.Date.from(Instant.now()).toInstant
  }
  
  protected def createStore(dbProvider: DatabaseProvider): S
}
