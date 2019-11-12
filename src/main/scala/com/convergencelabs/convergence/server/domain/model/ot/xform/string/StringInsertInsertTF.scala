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
 * This transformation function handles the case where a server
 * StringInsertOperation is concurrent with a client StringInsertOperation.
 * The major consideration in determining what transformation path to take
 * is the relative position of the two operation's positional indices.
 */
private[ot] object StringInsertInsertTF extends OperationTransformationFunction[StringInsertOperation, StringInsertOperation] {
  def transform(s: StringInsertOperation, c: StringInsertOperation): (StringOperation, StringOperation) = {
    if (s.index <= c.index) {
      // S-II-1 and S-II-2
      (s, c.copy(index = c.index + s.value.length))
    } else {
      // S-II-3
      (s.copy(index = s.index + c.value.length), c)
    }
  }
}
