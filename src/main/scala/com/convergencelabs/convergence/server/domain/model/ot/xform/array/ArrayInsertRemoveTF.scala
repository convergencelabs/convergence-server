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
 * This transformation function handles the transformation from a concurrent
 * server ArrayInsertOperation and a client ArrayRemoveOperation.  The primary
 * determination of the action to take is based on the relative location of
 * the insert and remove indices.
 */
private[ot] object ArrayInsertRemoveTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayRemoveOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayRemoveOperation): (ArrayInsertOperation, ArrayRemoveOperation) = {
    if (s.index <= c.index) {
      // A-IR-1 and A-IR-2
      (s, c.copy(index = c.index + 1))
    } else {
      // A-IR-3
      (s.copy(index = s.index - 1), c)
    }
  }
}
