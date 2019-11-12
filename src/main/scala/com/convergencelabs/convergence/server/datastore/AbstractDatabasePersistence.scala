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

package com.convergencelabs.convergence.server.datastore

import scala.util.Try

import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.orientechnologies.orient.core.db.document.ODatabaseDocument

abstract class AbstractDatabasePersistence(dbProvider: DatabaseProvider) {
  protected def tryWithDb[B](block: ODatabaseDocument => B): Try[B] =
    dbProvider.tryWithDatabase(block)

  protected def withDb[B](block: ODatabaseDocument => Try[B]): Try[B] =
    dbProvider.withDatabase(block)

  protected def withDb[B](db: Option[ODatabaseDocument])(block: ODatabaseDocument => Try[B]): Try[B] =
    db match {
      case Some(db) =>
        block(db)
      case None =>
        dbProvider.withDatabase(block)
    }
}
