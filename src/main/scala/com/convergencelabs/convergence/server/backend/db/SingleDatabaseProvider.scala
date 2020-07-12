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

import com.orientechnologies.orient.core.db.{ODatabase, OrientDB, OrientDBConfig}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument

import scala.util.{Failure, Success, Try}


final class SingleDatabaseProvider(serverUrl: String,
                                   database: String,
                                   username: String,
                                   password: String) extends DatabaseProvider {

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

  override def getDatabaseName: String = database
}
