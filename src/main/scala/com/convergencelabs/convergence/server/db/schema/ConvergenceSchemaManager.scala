/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.db.schema

import java.time.Instant

import com.convergencelabs.convergence.server.datastore.convergence.{ConvergenceDelta, ConvergenceDeltaHistory, DeltaHistoryStore}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.util.Try

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
