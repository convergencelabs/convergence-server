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

package com.convergencelabs.convergence.server.domain.model.ot.xform.array

import com.convergencelabs.convergence.server.domain.model.ot.xform.OperationTransformationFunction
import com.convergencelabs.convergence.server.domain.model.ot._

/**
 * This transformation function handles a concurrent server
 * ArrayInsertOperation and a client ArrayReplaceOperation.
 */
private[ot] object ArrayInsertReplaceTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayReplaceOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayReplaceOperation): (ArrayInsertOperation, ArrayReplaceOperation) = {
    if (s.index <= c.index) {
      // A-IP-1 and A-IP-2
      (s, c.copy(index = c.index + 1))
    } else {
      // A-IP-3
      (s, c)
    }
  }
}
