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

package com.convergencelabs.convergence.server.backend.db.schema

import java.time.Instant

import com.convergencelabs.convergence.server.backend.datastore.convergence.{DeltaHistoryStore, DomainDelta, DomainDeltaHistory}
import com.convergencelabs.convergence.server.model.DomainId
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import grizzled.slf4j.Logging
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.util.{Failure, Try}

class DomainSchemaManager(
  domainFqn: DomainId,
  db: ODatabaseDocument,
  historyStore: DeltaHistoryStore,
  preRelease: Boolean)
    extends AbstractSchemaManager(db, preRelease)
    with Logging {

  def getCurrentVersion(): Try[Int] = {
    this.historyStore.getDomainDBVersion(domainFqn)
  }

  def recordDeltaSuccess(delta: DeltaScript): Try[Unit] = {
    val dd = DomainDelta(delta.delta.version, delta.rawScript)
    val history = DomainDeltaHistory(domainFqn, dd, DeltaHistoryStore.Status.Success, None, Instant.now())
    this.historyStore.saveDomainDeltaHistory(history) recoverWith {
      case cause: Throwable =>
        error(s"Error creating delta history record for successful domain delta: \n${history}", cause)
        Failure(cause)
    }
  }

  def recordDeltaFailure(delta: DeltaScript, cause: Throwable): Unit = {
    val message = ExceptionUtils.getStackTrace(cause)
    val dd = DomainDelta(delta.delta.version, delta.rawScript)
    val history = DomainDeltaHistory(domainFqn, dd, DeltaHistoryStore.Status.Error, Some(message), Instant.now())
    this.historyStore.saveDomainDeltaHistory(history) recover {
      case cause: Exception =>
        error(s"Error creating delta history record for failed domain delta: ${history}", cause)
    }
  }

  def loadManifest(): Try[DeltaManifest] = {
    DeltaManager.domainManifest()
  }
}
