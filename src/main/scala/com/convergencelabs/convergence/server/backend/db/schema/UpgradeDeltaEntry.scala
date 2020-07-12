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

/**
 * Represents a Delta listed within a [[SchemaVersionManifest]].
 *
 * @param id     The id of the delta.
 * @param tag    Indicates that this is a derivation of the
 *               original delta. Commonly used for a backport
 *               or to indicate that the delta was implicitly
 *               applied during the initial installation.
 * @param sha256 The optional sha256 to check the delta against.
 */
private[schema] final case class UpgradeDeltaEntry(id: String,
                                                   tag: Option[String],
                                                   sha256: String) {
  /**
   * @return A corresponding DeltaId using this objects deltaId and
   *         backportTag fields.
   */
  def toDeltaId: UpgradeDeltaId = UpgradeDeltaId(id, tag)
}
