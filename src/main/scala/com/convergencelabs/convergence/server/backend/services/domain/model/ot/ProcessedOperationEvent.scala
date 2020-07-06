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

package com.convergencelabs.convergence.server.backend.services.domain.model.ot

/**
 * The ProcessedOperationEvent represents an operation that has been contextualized on to the servers state path.  This
 * is the final state the operation will be in as it enters the globally ordered operation history.
 *
 * @param clientId       The string id of the client that submitted the operation.
 * @param contextVersion The context version of the operation after any required transformation.
 * @param operation      The (potentially) transformed operation.
 */
final case class ProcessedOperationEvent(clientId: String, contextVersion: Long, operation: Operation) {
  val resultingVersion: Long = contextVersion + 1
}
