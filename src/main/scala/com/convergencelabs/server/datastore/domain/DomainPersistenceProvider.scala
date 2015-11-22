package com.convergencelabs.server.datastore.domain

import org.json4s.JsonAST.JValue
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import scala.util.Try
import scala.util.Success
import scala.util.Failure

class DomainPersistenceProvider(private[domain] val dbPool: OPartitionedDatabasePool) {

  val userStore = new DomainUserStore(dbPool)
  
  val configStore = new DomainConfigStore(dbPool)

  val modelStore = new ModelStore(dbPool)
  
  val operationStore = new OperationStore(dbPool)

  val modelOperationStore = new OperationHistoryStore(dbPool)

  val modelSnapshotStore = new ModelSnapshotStore(dbPool)
  
  def validateConnection(): Boolean = {
    Try[Unit](dbPool.acquire().close()) match {
      case Success(_) => true
      case Failure(_) => false
    }
  }
}