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

package com.convergencelabs.convergence.server.domain.model.reference

import com.convergencelabs.convergence.server.domain.DomainUserSessionId
import com.convergencelabs.convergence.server.domain.model.ot.xform.IndexTransformer

class RangeReference(
  modelValue: Any,
  session: DomainUserSessionId,
  key: String)
    extends ModelReference[(Int, Int)](modelValue, session, key)
    with PositionalInsertAware
    with PositionalRemoveAware
    with PositionalReorderAware {

  def handlePositionalInsert(index: Int, length: Int): Unit = {
    this.values = this.values.map { v =>
      val xFormed = IndexTransformer.handleInsert(List(v._1, v._2), index, length)
      (xFormed(0), xFormed(1))
    }
  }

  def handlePositionalRemove(index: Int, length: Int): Unit = {
    this.values = this.values.map { v =>
      val xFormed = IndexTransformer.handleRemove(List(v._1, v._2), index, length)
      (xFormed(0), xFormed(1))
    }
  }

  def handlePositionalReorder(fromIndex: Int, toIndex: Int): Unit = {
    this.values = this.values.map { v =>
      val xFormed = IndexTransformer.handleReorder(List(v._1, v._2), fromIndex, toIndex)
      (xFormed(0), xFormed(1))
    }
  }
}
