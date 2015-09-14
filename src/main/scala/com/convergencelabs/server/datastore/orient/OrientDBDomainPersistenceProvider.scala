package com.convergencelabs.server.datastore.orient

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.datastore.domain.ModelHistoryStore
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore

class OrientDBDomainPersistenceProvider extends DomainPersistenceProvider {

  val modelStore: ModelStore = ???

  val modelHistoryStore: ModelHistoryStore = ???

  val modelSnapshotStore: ModelSnapshotStore = ???

  val userStore: DomainUserStore = ???

  def init(databaseConfig: JValue): Unit = {

  }

  def dispose(): Unit = {

  }

}