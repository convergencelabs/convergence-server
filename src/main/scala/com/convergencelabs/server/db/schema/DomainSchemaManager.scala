/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.db.schema

import java.time.Instant

import scala.util.Failure
import scala.util.Try

import org.apache.commons.lang3.exception.ExceptionUtils

import com.convergencelabs.server.datastore.convergence.DeltaHistoryStore
import com.convergencelabs.server.datastore.convergence.DomainDelta
import com.convergencelabs.server.datastore.convergence.DomainDeltaHistory
import com.convergencelabs.server.domain.DomainId
import com.orientechnologies.orient.core.db.document.ODatabaseDocument

import grizzled.slf4j.Logging

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
