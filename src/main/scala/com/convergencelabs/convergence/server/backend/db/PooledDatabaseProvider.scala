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

package com.convergencelabs.convergence.server.backend.db

import com.convergencelabs.convergence.server.util.TryWithResource
import com.orientechnologies.orient.core.config.OGlobalConfiguration
import com.orientechnologies.orient.core.db.{ODatabasePool, OrientDB, OrientDBConfig}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument

import scala.util.{Failure, Success, Try}

final class PooledDatabaseProvider(serverUrl: String,
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

  override def getDatabaseName: String = {
    database
  }
}
