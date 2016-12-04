package com.convergencelabs.server.datastore

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import scala.util.Try
import scala.util.Success
import scala.util.Failure

abstract class AbstractPersistenceProvider(dbProvider: DatabaseProvider) {
  def validateConnection(): Try[Unit] = {
    dbProvider.validateConnection()
  }

  def shutdown(): Unit = {
    dbProvider.shutdown()
  }
}
