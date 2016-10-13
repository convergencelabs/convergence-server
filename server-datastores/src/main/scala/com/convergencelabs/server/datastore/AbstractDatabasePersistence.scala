package com.convergencelabs.server.datastore

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.util.TryWithResource
import scala.util.Try
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

abstract class AbstractDatabasePersistence(dbPool: OPartitionedDatabasePool) {
  protected def tryWithDb[B](block: ODatabaseDocumentTx => B): Try[B] =
    TryWithResource(dbPool.acquire())(block)
}
