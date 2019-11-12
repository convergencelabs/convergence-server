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

private[ot] object ObjectAddPropertyAddPropertyTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectAddPropertyOperation): (ObjectOperation, ObjectAddPropertyOperation) = {
    if (s.property != c.property) {
      // O-AA-1
      (s, c)
    } else if (s.value != c.value) {
      // O-AA-2
      val ObjectAddPropertyOperation(path, noOp, prop, value) = s
      (ObjectSetPropertyOperation(path, noOp, prop, value), c.copy(noOp = true))
    } else {
      // O-AA-3
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
