package com.convergencelabs.server.db.schema

import java.time.Instant

import scala.util.Try

import org.apache.commons.lang3.exception.ExceptionUtils

import com.convergencelabs.server.datastore.DeltaHistoryStore
import com.convergencelabs.server.domain.datastore.ConvergenceDelta
import com.convergencelabs.server.domain.datastore.ConvergenceDeltaHistory
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.datastore.DomainDelta
import com.convergencelabs.server.domain.datastore.DomainDeltaHistory
import grizzled.slf4j.Logging
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

class DomainSchemaManager(
  domainFqn: DomainFqn,
  db: ODatabaseDocumentTx,
  historyStore: DeltaHistoryStore,
  preRelease: Boolean)
    extends AbstractSchemaManager(db: ODatabaseDocumentTx, historyStore: DeltaHistoryStore, preRelease: Boolean)
    with Logging {

  def getCurrentVersion(): Try[Int] = {
    this.historyStore.getDomainDBVersion(domainFqn)
  }

  def recordDeltaSuccess(delta: DeltaScript): Unit = Try {
    val dd = DomainDelta(delta.delta.version, delta.rawScript)
    val history = DomainDeltaHistory(domainFqn, dd, DeltaHistoryStore.Status.Success, None, Instant.now())
    this.historyStore.saveDomainDeltaHistory(history) recover {
      case cause: Exception =>
        logger.error(s"Error creating detla history record for successful domain delta: ${history}")
    }
  }

  def recordDeltaFailure(delta: DeltaScript, cause: Exception): Unit = {
    val message = ExceptionUtils.getStackTrace(cause)
    val dd = DomainDelta(delta.delta.version, delta.rawScript)
    val history = DomainDeltaHistory(domainFqn, dd, DeltaHistoryStore.Status.Error, Some(message), Instant.now())
    this.historyStore.saveDomainDeltaHistory(history) recover {
      case cause: Exception =>
        logger.error(s"Error creating detla history record for failed domain delta: ${history}")
    }
  }

  def loadManifest(): Try[DeltaManifest] = {
    DeltaManager.domainManifest()
  }
}