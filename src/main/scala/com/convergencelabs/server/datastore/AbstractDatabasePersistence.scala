/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore

import scala.util.Try

import com.convergencelabs.server.db.DatabaseProvider
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
