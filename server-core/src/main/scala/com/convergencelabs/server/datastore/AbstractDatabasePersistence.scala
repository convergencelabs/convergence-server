package com.convergencelabs.server.datastore

import scala.util.Try

import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.convergencelabs.server.db.DatabaseProvider

abstract class AbstractDatabasePersistence(dbProvider: DatabaseProvider) {
  protected def tryWithDb[B](block: ODatabaseDocument => B): Try[B] =
    dbProvider.tryWithDatabase(block)

  protected def withDb[B](block: ODatabaseDocument => Try[B]): Try[B] =
    dbProvider.withDatabase(block)
}
