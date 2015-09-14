package com.convergencelabs.server.datastore.domain

import org.json4s.JsonAST.JValue

trait DomainPersistenceProvider {

  def init(databaseConfig: JValue): Unit

  def dispose(): Unit

  def modelStore: ModelStore

  def modelHistoryStore: ModelHistoryStore

  def modelSnapshotStore: ModelSnapshotStore

  def userStore: DomainUserStore
}