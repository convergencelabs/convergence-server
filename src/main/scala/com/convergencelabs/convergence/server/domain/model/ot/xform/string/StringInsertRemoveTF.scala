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

package com.convergencelabs.convergence.server.domain.model.ot.xform.string

import com.convergencelabs.convergence.server.domain.model.ot.xform.OperationTransformationFunction
import com.convergencelabs.convergence.server.domain.model.ot._

private[ot] object StringInsertRemoveTF extends OperationTransformationFunction[StringInsertOperation, StringRemoveOperation] {
  def transform(s: StringInsertOperation, c: StringRemoveOperation): (StringOperation, StringOperation) = {
    if (s.index <= c.index) {
      // S-IR-1 and S-IR-2
      (s, c.copy(index = c.index + s.value.length))
    } else if (s.index >= c.index + c.value.length ) {
      // S-IR-5
      (s.copy(index = s.index - c.value.length), c)
    } else {
      // S-IR-3 and S-IR-4
      val offsetDelta = s.index - c.index
      (s.copy(noOp = true),
        c.copy(
          value = c.value.substring(0, offsetDelta) +
            s.value +
            c.value.substring(offsetDelta, c.value.length)))
    }
  }
}
