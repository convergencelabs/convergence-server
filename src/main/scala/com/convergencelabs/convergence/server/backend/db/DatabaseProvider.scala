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
