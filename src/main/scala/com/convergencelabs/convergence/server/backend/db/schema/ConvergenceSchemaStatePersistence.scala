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

import com.convergencelabs.convergence.server.backend.datastore.convergence.{ConvergenceSchemaDeltaLogEntry, ConvergenceSchemaDeltaLogStore, ConvergenceSchemaVersionLogEntry, ConvergenceSchemaVersionLogStore, SchemaDeltaStatus}

import scala.util.Try

class ConvergenceSchemaStatePersistence(deltaStore: ConvergenceSchemaDeltaLogStore,
                                        versionStore: ConvergenceSchemaVersionLogStore) extends SchemaStatePersistence {
  /**
   * Gets the current version of the installed schema.
   *
   * @return None if the schema is empty, or Some with a version if the schema
   *         has been installed.
   */
  override def installedVersion(): Try[Option[String]] = {
    versionStore.getConvergenceSchemaVersion()
  }

  /**
   * Returns the list of deltas that are applied to the current schema. This
   * list includes the deltas that were implicitly applied when the schema
   * was installed.
   *
   * @return A list of [[UpgradeDeltaId]]s of all of the deltas that this schema
   *         has had applied.
   */
  override def appliedDeltas(): Try[List[UpgradeDeltaId]] = {
    deltaStore.appliedConvergenceDeltas().map(deltas => {
      deltas.map(d => UpgradeDeltaId(d.id, d.tag))
    })
  }

  /**
   * Records that a delta was successfully applied. This will store the delta
   * meta data as well as the raw script. Storing the raw script allows
   * a historical view of exactly how the database got to the state that
   * it is in.
   *
   * @param delta The delta that was applied.
   * @param appliedForVersion The version that was being upgraded to when
   *                          this was applied.
   * @return A Success if recording the delta application succeeded, a Failure
   *         otherwise.
   */
  override def recordDeltaSuccess(delta: UpgradeDeltaAndScript, appliedForVersion: String): Try[Unit] = {
    val UpgradeDeltaAndScript(deltaId, _, script) = delta
    for {
      seqNo <- deltaStore.getMaxDeltaSequenceNumber()
      entry = ConvergenceSchemaDeltaLogEntry(seqNo + 1, deltaId.id, deltaId.tag, script, SchemaDeltaStatus.Success, None, Instant.now())
      _ <- deltaStore.createConvergenceDeltaEntries(List(entry), appliedForVersion)
    } yield ()
  }

  /**
   * Records that a delta failed to apply.  This will store the delta meta
   * data, the raw script, and the error. The stack trace
   *
   * @param delta             The delta that was applied.
   * @param error             An error message detailing the failure.
   * @param appliedForVersion The version that was being upgraded to when
   *                          this delta failed.
   * @return A Success if recording the delta failure succeeded, a Failure
   *         otherwise.
   */
  override def recordDeltaFailure(delta: UpgradeDeltaAndScript, error: String, appliedForVersion: String): Unit = {
    val UpgradeDeltaAndScript(deltaId, _, script) = delta
    for {
      seqNo <- deltaStore.getMaxDeltaSequenceNumber()
      entry = ConvergenceSchemaDeltaLogEntry(seqNo + 1, deltaId.id, deltaId.tag, script, SchemaDeltaStatus.Error, Some(error), Instant.now())
      _ <- deltaStore.createConvergenceDeltaEntries(List(entry), appliedForVersion)
    } yield ()
  }

  /**
   * Stores the fact that a set of deltas have effectively been applied as
   * part of the installation process. Each install schema is essentially
   * a summation of all of the deltas, so these deltas need to be marked
   * as being applied.
   *
   * @param deltaIds          The list of deltas to mark as implicitly
   *                          installed.
   * @param appliedForVersion The version that was being installed that
   *                          applied this version.
   * @return A Success if recording succeeds or a Failure otherwise.
   */
  override def recordImplicitDeltasFromInstall(deltaIds: List[UpgradeDeltaId], appliedForVersion: String): Try[Unit] = {
    val entries = createEntries(deltaIds)
    deltaStore.createConvergenceDeltaEntries(entries, appliedForVersion)
  }

  private[this] def createEntries(deltaIds: List[UpgradeDeltaId]): List[ConvergenceSchemaDeltaLogEntry] = {
    var curSeqNo = 1
    deltaIds.map { deltaId =>
      val seqNo = curSeqNo
      curSeqNo += 1
      ConvergenceSchemaDeltaLogEntry(seqNo, deltaId.id, deltaId.tag, "", SchemaDeltaStatus.Success, None, Instant.now())
    }
  }

  override def recordNewVersion(version: String, date: Instant): Try[Unit] = {
    val entry = ConvergenceSchemaVersionLogEntry(version, date)
    versionStore.createConvergenceSchemaVersionLogEntry(entry)
  }
}
