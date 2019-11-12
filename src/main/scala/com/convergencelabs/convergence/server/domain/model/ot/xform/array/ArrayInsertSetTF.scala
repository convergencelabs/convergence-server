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

package com.convergencelabs.convergence.server.domain.model.ot

/**
 * This transformation function handles a concurrent server
 * ArrayInsertOperation and a client ArraySetOperation.  In every case, the
 * set operation will be preserved while the insert will be No-Op'ed
 */
private[ot] object ArrayInsertSetTF extends OperationTransformationFunction[ArrayInsertOperation, ArraySetOperation] {
  def transform(s: ArrayInsertOperation, c: ArraySetOperation): (ArrayInsertOperation, ArraySetOperation) = {
    // A-IS-1
    (s.copy(noOp = true), c)
  }
}
