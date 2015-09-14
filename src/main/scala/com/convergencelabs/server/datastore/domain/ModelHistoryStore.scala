package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.domain.model.ModelFqn

trait ModelHistoryStore {
  def getMaxVersion(modelFqn: ModelFqn): Long

  def getVersionAtOrBeforeTime(modelFqn: ModelFqn, time: Long): Long

  def getOperationsAfterVersion(modelFqn: ModelFqn, startVersion: Long): List[OperationEvent]

  def getOperationsAfterVersion(modelFqn: ModelFqn, version: Long, limit: Int): List[OperationEvent]

  def removeHistoryForModel(modelFqn: ModelFqn): Unit
}

case class OperationEvent()