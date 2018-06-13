package com.convergencelabs.server.datastore.domain

import java.util.concurrent.atomic.AtomicInteger

import com.convergencelabs.server.db.ConnectedSingleDatabaseProvider
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.db.schema.TestingSchemaManager
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.ODatabaseType
import com.orientechnologies.orient.core.db.OrientDB
import com.orientechnologies.orient.core.db.OrientDBConfig

abstract class PersistenceStoreSpec[S](category: DeltaCategory.Value) {
  OLogManager.instance().setConsoleLevel("WARNING")

  protected def createStore(dbProvider: DatabaseProvider): S

  private[this] val dbCounter = new AtomicInteger(1)

  def withPersistenceStore(testCode: S => Any): Unit = {
    // make sure no accidental collisions
    val dbName = s"${getClass.getSimpleName}${nextDbId()}"

    // TODO we could probably cache this in a before / after all.
    val orientDB = new OrientDB("memory:", "root", "password", OrientDBConfig.defaultConfig());
    orientDB.create(dbName, ODatabaseType.MEMORY);
    val db = orientDB.open(dbName, "admin", "admin")
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
      orientDB.close()
    }
  }

  def nextDbId(): Int = {
    dbCounter.getAndIncrement()
  }

  object CommandListener extends OCommandOutputListener() {
    def onMessage(iText: String): Unit = {
    }
  }
}
