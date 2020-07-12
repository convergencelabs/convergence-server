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

import com.orientechnologies.orient.core.db.ODatabase
import com.orientechnologies.orient.core.db.document.ODatabaseDocument

import scala.util.{Failure, Success, Try}

final class ConnectedSingleDatabaseProvider(db: ODatabaseDocument) extends DatabaseProvider {

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

  override def getDatabaseName: String = db.getName
}
