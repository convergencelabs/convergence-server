package com.convergencelabs.server.db

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.util.TryWithResource
import com.orientechnologies.orient.core.db.ODatabasePool
import com.orientechnologies.orient.core.db.OrientDB
import com.orientechnologies.orient.core.db.OrientDBConfig
import com.orientechnologies.orient.core.db.document.ODatabaseDocument

trait DatabaseProvider {
  def connect(): Try[Unit]
  def tryWithDatabase[B](f: (ODatabaseDocument) => B): Try[B]
  def withDatabase[B](f: (ODatabaseDocument) => Try[B]): Try[B]
  def validateConnection(): Try[Unit]
  def shutdown(): Unit

  protected[db] val NotConnected = "Orient DB is not connected."
}

class SingleDatabaseProvider(serverUrl: String, database: String, username: String, password: String) extends DatabaseProvider {

  var orientDb: Option[OrientDB] = None
  var db: Option[ODatabaseDocument] = None

  def connect(): Try[Unit] = Try {
    val orientDb = new OrientDB(serverUrl, OrientDBConfig.defaultConfig())
    this.db = Some(orientDb.open(database, username, password))
    this.orientDb = Some(orientDb)
  }

  def tryWithDatabase[B](f: (ODatabaseDocument) => B): Try[B] = {
    assertConnected().flatMap { db =>
      Try {
        db.activateOnCurrentThread()
        f(db)
      }
    }
  }

  def withDatabase[B](f: (ODatabaseDocument) => Try[B]): Try[B] = {
    assertConnected().flatMap { db =>
      db.activateOnCurrentThread()
      f(db)
    }
  }

  def validateConnection(): Try[Unit] = Try {
    // FIXME what do to here
  }

  def shutdown(): Unit = {
    db.foreach { db =>
      db.activateOnCurrentThread()
      db.close()
    }

    orientDb.foreach(_.close())
  }

  protected[this] def assertConnected(): Try[ODatabaseDocument] = {
    this.db match {
      case Some(db) => Success(db)
      case None => Failure(new IllegalStateException(this.NotConnected))
    }
  }
}

class ConnectedSingleDatabaseProvider(db: ODatabaseDocument) extends DatabaseProvider {

  def connect(): Try[Unit] = Success(())

  def tryWithDatabase[B](f: (ODatabaseDocument) => B): Try[B] = {
    Try {
      db.activateOnCurrentThread()
      f(db)
    }
  }

  def withDatabase[B](f: (ODatabaseDocument) => Try[B]): Try[B] = {
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

// FIXME figure out how to configure the pool.
class PooledDatabaseProvider(serverUrl: String, database: String, username: String, password: String) extends DatabaseProvider {
  var orientDb: Option[OrientDB] = None
  var dbPool: Option[ODatabasePool] = None

  def connect(): Try[Unit] = Try {
    val orientDb = new OrientDB(serverUrl, OrientDBConfig.defaultConfig())
    this.dbPool = Some(new ODatabasePool(orientDb, database, username, password))
    this.orientDb = Some(orientDb)
  }

  def tryWithDatabase[B](f: (ODatabaseDocument) => B): Try[B] = {
    assertConnected.flatMap { dbPool =>
      TryWithResource(dbPool.acquire()) { db =>
        db.activateOnCurrentThread()
        val result = f(db)
        db.activateOnCurrentThread()
        result
      }
    }
  }

  def withDatabase[B](f: (ODatabaseDocument) => Try[B]): Try[B] = {
    assertConnected.flatMap { dbPool =>
      TryWithResource(dbPool.acquire()) { db =>
        db.activateOnCurrentThread()
        val result = f(db)
        db.activateOnCurrentThread()
        result.get
      }
    }
  }

  def validateConnection(): Try[Unit] = {
    assertConnected.flatMap(dbPool => Try[Unit](dbPool.acquire().close()))
  }

  def shutdown(): Unit = {
    dbPool.foreach(_.close())
    orientDb.foreach(_.close())
  }

  protected[this] def assertConnected(): Try[ODatabasePool] = {
    this.dbPool match {
      case Some(dbPool) => Success(dbPool)
      case None => Failure(new IllegalStateException(this.NotConnected))
    }
  }
}