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

package com.convergencelabs.convergence.server.db

import com.convergencelabs.convergence.server.util.TryWithResource
import com.orientechnologies.orient.core.config.OGlobalConfiguration
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.{ODatabase, ODatabasePool, OrientDB, OrientDBConfig}

import scala.util.{Failure, Success, Try}

/**
 * A trait that provides methods for performing operations wth a database
 * connection. This trait abstracts over various implementations that
 * can provide a database for persistence operations.
 */
trait DatabaseProvider {
  def connect(): Try[Unit]

  def tryWithDatabase[B](f: ODatabaseDocument => B): Try[B]

  def withDatabase[B](f: ODatabaseDocument => Try[B]): Try[B]

  def validateConnection(): Try[Unit]

  def shutdown(): Unit

  protected[db] val NotConnected = "The database is not connected."
}

class SingleDatabaseProvider(serverUrl: String, database: String, username: String, password: String) extends DatabaseProvider {

  var orientDb: Option[OrientDB] = None
  var db: Option[ODatabaseDocument] = None

  def connect(): Try[Unit] = Try {
    val orientDb = new OrientDB(serverUrl, OrientDBConfig.defaultConfig())
    this.db = Some(orientDb.open(database, username, password))
    this.orientDb = Some(orientDb)
  }

  def tryWithDatabase[B](f: ODatabaseDocument => B): Try[B] = {
    assertConnected().flatMap { db =>
      Try {
        db.activateOnCurrentThread()
        f(db)
      }
    }
  }

  def withDatabase[B](f: ODatabaseDocument => Try[B]): Try[B] = {
    assertConnected().flatMap { db =>
      db.activateOnCurrentThread()
      f(db)
    }
  }

  def validateConnection(): Try[Unit] = Try {
    this.assertConnected().map(_ => ())
  }

  def shutdown(): Unit = {
    db.foreach { db =>
      db.activateOnCurrentThread()
      db.close()
    }

    orientDb.foreach(_.close())

    orientDb = None
    db = None
  }

  protected[this] def assertConnected(): Try[ODatabaseDocument] = {
    db match {
      case Some(connection) =>
        if (connection.getStatus == ODatabase.STATUS.OPEN) {
          Success(connection)
        } else {
          Failure(new IllegalStateException(s"The database state was: ${connection.getStatus}"))
        }
      case None =>
        Failure(new IllegalStateException(this.NotConnected))
    }
  }
}

class ConnectedSingleDatabaseProvider(db: ODatabaseDocument) extends DatabaseProvider {

  def connect(): Try[Unit] = Success(())

  def tryWithDatabase[B](f: ODatabaseDocument => B): Try[B] = {
    Try {
      db.activateOnCurrentThread()
      f(db)
    }
  }

  def withDatabase[B](f: ODatabaseDocument => Try[B]): Try[B] = {
    db.activateOnCurrentThread()
    f(db)
  }

  def validateConnection(): Try[Unit] = Try {
    if (db.getStatus == ODatabase.STATUS.OPEN) {
      Success(())
    } else {
      Failure(new IllegalStateException(s"The database state was: ${db.getStatus}"))
    }
  }

  def shutdown(): Unit = {
    db.activateOnCurrentThread()
    db.close()
  }
}

class PooledDatabaseProvider(serverUrl: String,
                             database: String,
                             username: String,
                             password: String,
                             poolMin: Int,
                             poolMax: Int
                            ) extends DatabaseProvider {
  var orientDb: Option[OrientDB] = None
  var dbPool: Option[ODatabasePool] = None

  def connect(): Try[Unit] = Try {

    val poolCfg = OrientDBConfig.builder
    poolCfg.addConfig(OGlobalConfiguration.DB_POOL_MIN, poolMin)
    poolCfg.addConfig(OGlobalConfiguration.DB_POOL_MAX, poolMax)

    val orientDb = new OrientDB(serverUrl, poolCfg.build())

    this.dbPool = Some(new ODatabasePool(orientDb, database, username, password))
    this.orientDb = Some(orientDb)
  }

  def tryWithDatabase[B](f: ODatabaseDocument => B): Try[B] = {
    assertConnected().flatMap { dbPool =>
      TryWithResource(dbPool.acquire()) { db =>
        db.activateOnCurrentThread()
        val result = f(db)
        db.activateOnCurrentThread()
        result
      }
    }
  }

  def withDatabase[B](f: ODatabaseDocument => Try[B]): Try[B] = {
    assertConnected().flatMap { dbPool =>
      TryWithResource(dbPool.acquire()) { db =>
        db.activateOnCurrentThread()
        val result = f(db)
        db.activateOnCurrentThread()
        result.get
      }
    }
  }

  def validateConnection(): Try[Unit] = {
    assertConnected().flatMap(dbPool => Try[Unit](dbPool.acquire().close()))
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