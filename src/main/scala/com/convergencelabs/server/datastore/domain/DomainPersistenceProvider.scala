package com.convergencelabs.server.datastore.domain

import org.json4s.JsonAST.JValue

abstract class DomainPersistenceProvider(databaseConfig: JValue) {

  def dispose(): Unit

  def modelStore: ModelStore

  def modelHistoryStore: ModelHistoryStore

  def modelSnapshotStore: ModelSnapshotStore

  def userStore: DomainUserStore
}