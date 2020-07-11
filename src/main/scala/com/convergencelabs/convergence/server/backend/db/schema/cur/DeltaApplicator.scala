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
 * The [[DeltaApplicator]] trait is a simple interface that allows the
 * [[SchemaManager]] to apply a delta to a database schema.
 */
private[schema] trait DeltaApplicator {

  /**
   * Applies the delta to the existing schema (which could be empty).
   *
   * @param delta The delta to apply.
   * @return A Success if the application succeeds, or a Failure otherwise.
   */
  def applyDeltaToSchema(delta: Delta): Try[Unit]
}
