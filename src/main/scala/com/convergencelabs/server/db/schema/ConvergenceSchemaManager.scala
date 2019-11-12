/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.db.schema

import java.time.Instant

import scala.util.Try

import org.apache.commons.lang3.exception.ExceptionUtils

import com.convergencelabs.server.datastore.convergence.ConvergenceDelta
import com.convergencelabs.server.datastore.convergence.ConvergenceDeltaHistory
import com.convergencelabs.server.datastore.convergence.DeltaHistoryStore
import com.orientechnologies.orient.core.db.document.ODatabaseDocument

class ConvergenceSchemaManager(db: ODatabaseDocument, historyStore: DeltaHistoryStore, preRelease: Boolean)
    extends AbstractSchemaManager(db, preRelease: Boolean) {

  def getCurrentVersion(): Try[Int] = {
    this.historyStore.getConvergenceDBVersion()
  }

  def recordDeltaSuccess(delta: DeltaScript): Try[Unit] = Try {
    val cd = ConvergenceDelta(delta.delta.version, delta.rawScript)
    val history = ConvergenceDeltaHistory(cd, DeltaHistoryStore.Status.Success, None, Instant.now())
    this.historyStore.saveConvergenceDeltaHistory(history)
  }

  def recordDeltaFailure(delta: DeltaScript, cause: Throwable): Unit = {
    val cd = ConvergenceDelta(delta.delta.version, delta.rawScript)
    val message = ExceptionUtils.getStackTrace(cause)
    val history = ConvergenceDeltaHistory(cd, DeltaHistoryStore.Status.Error, Some(message), Instant.now())
    this.historyStore.saveConvergenceDeltaHistory(history)
  }

  def loadManifest(): Try[DeltaManifest] = {
    DeltaManager.convergenceManifest()
  }
}
