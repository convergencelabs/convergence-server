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
import org.scalatest.WordSpec
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

object PersistenceStoreSpec {
  val OrientDBAdmin = "admin"
}

abstract class PersistenceStoreSpec[S](category: DeltaCategory.Value)
  extends WordSpec
  with Matchers
  with BeforeAndAfterAll {
  
  import PersistenceStoreSpec._
  
  OLogManager.instance().setConsoleLevel("WARNING")

  private[this] val dbCounter = new AtomicInteger(1)
  private[this] val orientDB: OrientDB = new OrientDB(s"memory:${getClass.getSimpleName}", OrientDBConfig.defaultConfig());

  override protected def afterAll() = {
    orientDB.close();
  }
  
  def withPersistenceStore(testCode: S => Any): Unit = {
    // make sure no accidental collisions
    val dbName = s"${getClass.getSimpleName}${nextDbId}"

    orientDB.create(dbName, ODatabaseType.MEMORY);
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
  
  protected def createStore(dbProvider: DatabaseProvider): S
}
