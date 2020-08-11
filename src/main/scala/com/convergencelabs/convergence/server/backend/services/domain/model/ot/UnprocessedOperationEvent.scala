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
 * The UnprocessedOperationEvent represents a incoming operation from the a client.  The operation may not be
 * contextualized to the head of the servers state path, and may therefore require transformation.
 *
 * @param clientId       The string id of the client that submitted the operation.
 * @param contextVersion The context version of the operation when it was generated by the client.  This is the point at
 *                       which the client branched of the servers state path.
 * @param operation      The operation that was performed.
 */
final case class UnprocessedOperationEvent(clientId: String, contextVersion: Long, operation: Operation)