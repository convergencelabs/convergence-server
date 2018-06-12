package com.convergencelabs.server.datastore

import scala.util.Try

import com.convergencelabs.server.util.TryWithResource
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.ODatabasePool

object DatabaseProvider {
  def apply(db: ODatabaseDocument): DatabaseProvider = {
    new SingleDatabaseProvider(db)
  }
  
  def apply(dbPool: ODatabasePool): DatabaseProvider = {
    new PooledDatabaseProvider(dbPool)
  }
}

trait DatabaseProvider {
  def tryWithDatabase[B](f: (ODatabaseDocument) => B): Try[B]
  def withDatabase[B](f: (ODatabaseDocument) => Try[B]): Try[B]
  def validateConnection(): Try[Unit]
  def shutdown(): Unit
}

class SingleDatabaseProvider(db: ODatabaseDocument) extends DatabaseProvider {
  def tryWithDatabase[B](f: (ODatabaseDocument) => B ): Try[B] = Try {
    db.activateOnCurrentThread()
    f(db)
  }
  
  def withDatabase[B](f: (ODatabaseDocument) => Try[B] ): Try[B] = {
    db.activateOnCurrentThread()
    f(db)
  }
  
  
  def validateConnection(): Try[Unit] = Try {
    // FIXME what do to here
  }
  
  def shutdown(): Unit = {
    db.activateOnCurrentThread()
    db.close()
  }
}

class PooledDatabaseProvider(dbPool: ODatabasePool) extends DatabaseProvider {
  def tryWithDatabase[B](f: (ODatabaseDocument) => B ): Try[B] = {
    TryWithResource(dbPool.acquire()) { db =>
      f(db)
    }
  }
  
  def withDatabase[B](f: (ODatabaseDocument) => Try[B] ): Try[B] = {
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