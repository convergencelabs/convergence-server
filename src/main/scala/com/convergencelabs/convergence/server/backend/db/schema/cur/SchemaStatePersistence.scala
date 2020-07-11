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

package com.convergencelabs.convergence.server.backend.db.schema.cur

import com.convergencelabs.convergence.server.backend.db.schema.cur.delta.Delta

import scala.util.Try

/**
 * The [[SchemaStatePersistence]] trait provides methods to determine and
 * update the state of a particular schema.
 */
private[schema] trait SchemaStatePersistence {
  /**
   * Gets the current version of the installed schema.
   *
   * @return None if the schema is empty, or Some with a version if the schema
   *         has been installed.
   */
  def installedVersion(): Try[Option[String]]

  /**
   * Returns the list of deltas that are applied to the current schema. This
   * list includes the deltas that were implicitly applied when the schema
   * was installed.
   *
   * @return A list of [[UpgradeDeltaId]]s of all of the deltas that this schema
   *         has had applied.
   */
  def appliedDeltas(): Try[List[UpgradeDeltaId]]

  /**
   * Stores the fact that a set of deltas have effectively been applied as
   * part of the installation process. Each install schema is essentially
   * a summation of all of the deltas, so these deltas need to be marked
   * as being applied.
   *
   * @param deltas The list of deltas to mark as implicitly installed.
   * @return A Success if recording succeeds or a Failure otherwise.
   */
  def recordImplicitDeltasFromInstall(deltas: List[UpgradeDeltaEntry]): Try[Unit]

  /**
   * Records that a delta was successfully applied. This will store the delta
   * meta data as well as the raw script. Storing the raw script allows
   * a historical view of exactly how the database got to the state that
   * it is in.
   *
   * @param delta The delta that was applied.
   * @return A Success if recording the delta application succeeded, a Failure
   *         otherwise.
   */
  def recordDeltaSuccess(delta: UpgradeDeltaAndScript): Try[Unit]

  /**
   * Records that a delta failed to apply.  This will store the delta meta
   * data, the raw script, and the error. The stack trace
   *
   * @param delta The delta that was applied.
   * @param error An error message detailing the failure.
   * @return A Success if recording the delta failure succeeded, a Failure
   *         otherwise.
   */
  def recordDeltaFailure(delta: UpgradeDeltaAndScript, error: String): Unit

  /**
   * Applies the delta to the existing schema (which could be empty).
   *
   * @param delta The delta to apply.
   * @return A Success if the application succeeds, or a Failure otherwise.
   */
  def applyDeltaToSchema(delta: Delta): Try[Unit]
}
