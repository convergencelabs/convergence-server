package com.convergencelabs.server.datastore

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.util.TryWithResource
import scala.util.Try
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

abstract class AbstractDatabasePersistence(dbProvider: DatabaseProvider) {
  protected def tryWithDb[B](block: ODatabaseDocumentTx => B): Try[B] =
    dbProvider.tryWithDatabase(block)
}
