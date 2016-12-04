package com.convergencelabs.server.db.schema

import java.time.Instant

import scala.util.Try

import org.apache.commons.lang3.exception.ExceptionUtils

import com.convergencelabs.server.datastore.DeltaHistoryStore
import com.convergencelabs.server.domain.datastore.ConvergenceDelta
import com.convergencelabs.server.domain.datastore.ConvergenceDeltaHistory
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

class ConvergenceSchemaManager(db: ODatabaseDocumentTx, historyStore: DeltaHistoryStore, preRelease: Boolean)
    extends AbstractSchemaManager(db: ODatabaseDocumentTx, historyStore: DeltaHistoryStore, preRelease: Boolean) {

  def getCurrentVersion(): Try[Int] = {
    this.historyStore.getConvergenceDBVersion()
  }

  def recordDeltaSuccess(delta: DeltaScript): Unit = Try {
    val cd = ConvergenceDelta(delta.delta.version, delta.rawScript)
    val history = ConvergenceDeltaHistory(cd, DeltaHistoryStore.Status.Success, None, Instant.now())
    this.historyStore.saveConvergenceDeltaHistory(history)
  }

  def recordDeltaFailure(delta: DeltaScript, cause: Exception): Unit = {
    val cd = ConvergenceDelta(delta.delta.version, delta.rawScript)
    val message = ExceptionUtils.getStackTrace(cause)
    val history = ConvergenceDeltaHistory(cd, DeltaHistoryStore.Status.Error, Some(message), Instant.now())
    this.historyStore.saveConvergenceDeltaHistory(history)
  }

  def loadManifest(): Try[DeltaManifest] = {
    DeltaManager.convergenceManifest()
  }
}