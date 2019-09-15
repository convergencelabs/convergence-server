package com.convergencelabs.server.datastore

import scala.util.Try
import com.convergencelabs.server.db.DatabaseProvider

abstract class AbstractPersistenceProvider(dbProvider: DatabaseProvider) {
  def validateConnection(): Try[Unit] = {
    dbProvider.validateConnection()
  }

  def shutdown(): Unit = {
    dbProvider.shutdown()
  }
}
