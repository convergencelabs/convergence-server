package com.convergencelabs.server.datastore

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.util.TryWithResource
import scala.util.Try

object DatabaseProvider {
  def apply(db: ODatabaseDocumentTx): DatabaseProvider = {
    new SingleDatabaseProvider(db)
  }
  
  def apply(dbPool: OPartitionedDatabasePool): DatabaseProvider = {
    new PooledDatabaseProvider(dbPool)
  }
}

trait DatabaseProvider {
  def tryWithDatabase[B](f: (ODatabaseDocumentTx) => B): Try[B]
  def withDatabase[B](f: (ODatabaseDocumentTx) => Try[B]): Try[B]
  def validateConnection(): Try[Unit]
  def shutdown(): Unit
}

class SingleDatabaseProvider(db: ODatabaseDocumentTx) extends DatabaseProvider {
  def tryWithDatabase[B](f: (ODatabaseDocumentTx) => B ): Try[B] = Try {
    db.activateOnCurrentThread()
    f(db)
  }
  
  def withDatabase[B](f: (ODatabaseDocumentTx) => Try[B] ): Try[B] = {
    db.activateOnCurrentThread()
    f(db)
  }
  
  
  def validateConnection(): Try[Unit] = Try {
    // FIXME what do to here
  }
  
  def shutdown(): Unit = {
    db.close()
  }
}

class PooledDatabaseProvider(dbPool: OPartitionedDatabasePool) extends DatabaseProvider {
  def tryWithDatabase[B](f: (ODatabaseDocumentTx) => B ): Try[B] = {
    TryWithResource(dbPool.acquire()) { db =>
      f(db)
    }
  }
  
  def withDatabase[B](f: (ODatabaseDocumentTx) => Try[B] ): Try[B] = {
    TryWithResource(dbPool.acquire()) { db =>
      f(db).get
    }
  }
  
  def validateConnection(): Try[Unit] = {
    Try[Unit](dbPool.acquire().close())
  }

  def shutdown(): Unit = {
    dbPool.close()
  }
}