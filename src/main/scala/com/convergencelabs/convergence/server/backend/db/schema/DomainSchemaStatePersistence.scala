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

import com.convergencelabs.convergence.server.backend.datastore.convergence.{DomainSchemaDeltaLogEntry, DomainSchemaDeltaLogStore, DomainSchemaVersionLogEntry, DomainSchemaVersionLogStore, SchemaDeltaStatus}
import com.convergencelabs.convergence.server.model.DomainId

import scala.util.Try

private[schema] class DomainSchemaStatePersistence(domainId: DomainId,
                                   deltaStore: DomainSchemaDeltaLogStore,
                                   versionStore: DomainSchemaVersionLogStore)
  extends SchemaStatePersistence {

  override def installedVersion(): Try[Option[String]] = {
    versionStore.getDomainSchemaVersion(domainId)
  }

  override def appliedDeltas(): Try[List[UpgradeDeltaId]] = {
    deltaStore.appliedDeltasForDomain(domainId).map(deltas => {
      deltas.map(d => UpgradeDeltaId(d.id, d.tag))
    })
  }

  override def recordDeltaSuccess(delta: UpgradeDeltaAndScript, appliedForVersion: String): Try[Unit] = {
    val UpgradeDeltaAndScript(deltaId, _, script) = delta
    for {
      seqNo <- deltaStore.getMaxDeltaSequenceNumber(domainId)
      entry = DomainSchemaDeltaLogEntry(domainId, seqNo + 1, deltaId.id, deltaId.tag, script, SchemaDeltaStatus.Success, None, Instant.now())
      _ <- deltaStore.createDomainDeltaLogEntries(List(entry), appliedForVersion)
    } yield ()
  }

  override def recordDeltaFailure(delta: UpgradeDeltaAndScript, error: String, appliedForVersion: String): Unit = {
    val UpgradeDeltaAndScript(deltaId, _, script) = delta
    for {
      seqNo <- deltaStore.getMaxDeltaSequenceNumber(domainId)
      entry = DomainSchemaDeltaLogEntry(domainId, seqNo + 1, deltaId.id, deltaId.tag, script, SchemaDeltaStatus.Error, Some(error), Instant.now())
      _ <- deltaStore.createDomainDeltaLogEntries(List(entry), appliedForVersion)
    } yield ()
  }

  override def recordImplicitDeltasFromInstall(deltaIds: List[UpgradeDeltaId], appliedForVersion: String): Try[Unit] = {
    val entries = createEntries(deltaIds)
    deltaStore.createDomainDeltaLogEntries(entries, appliedForVersion)
  }

  private[this] def createEntries(deltaIds: List[UpgradeDeltaId]): List[DomainSchemaDeltaLogEntry] = {
    var curSeqNo = 1
    deltaIds.map { deltaId =>
      val seqNo = curSeqNo
      curSeqNo += 1
      DomainSchemaDeltaLogEntry(domainId, seqNo, deltaId.id, deltaId.tag, "", SchemaDeltaStatus.Success, None, Instant.now())
    }
  }

  override def recordNewVersion(version: String, date: Instant): Try[Unit] = {
    val entry = DomainSchemaVersionLogEntry(domainId, version, date)
    versionStore.createDomainSchemaVersionLogEntry(entry)
  }
}
