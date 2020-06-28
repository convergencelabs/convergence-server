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

import com.convergencelabs.convergence.server.domain.model.ot._
import com.convergencelabs.convergence.server.domain.model.ot.xform.OperationTransformationFunction

private[ot] object ArrayRemoveReplaceTF extends OperationTransformationFunction[ArrayRemoveOperation, ArrayReplaceOperation] {
  def transform(s: ArrayRemoveOperation, c: ArrayReplaceOperation): (ArrayRemoveOperation, ArrayOperation) = {
    if (s.index < c.index) {
      // A-RP-1
      (s, c.copy(index = c.index - 1))
    } else if (s.index == c.index) {
      // A-RP-2
      val ArrayReplaceOperation(path, noOp, index, newValue) = c
      (s.copy(noOp = true), ArrayInsertOperation(path, noOp, index, newValue))
    }  else {
      // A-RP-3
      (s, c)
    }
  }
}
