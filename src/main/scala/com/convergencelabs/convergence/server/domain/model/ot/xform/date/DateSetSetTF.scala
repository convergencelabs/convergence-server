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

private[ot] object DateSetSetTF extends OperationTransformationFunction[DateSetOperation, DateSetOperation] {
  def transform(s: DateSetOperation, c: DateSetOperation): (DateOperation, DateOperation) = {
    if (s.value == c.value) {
      // D-SS-1
      (s.copy(noOp = true), s.copy(noOp = true))
    } else {
      // D-SS-2
      (s, c.copy(noOp = true))
    }
  }
}
