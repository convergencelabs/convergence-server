package com.convergencelabs.server.datastore

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import scala.util.Try
import scala.util.Success
import scala.util.Failure

abstract class AbstractPersistenceProvider(dbPool: OPartitionedDatabasePool) {
  def validateConnection(): Try[Unit] = {
    Try[Unit](dbPool.acquire().close())
  }

  def shutdown(): Unit = {
    dbPool.close()
  }
}
