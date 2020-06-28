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
import com.convergencelabs.convergence.server.domain.model.{IndexReferenceValues, ModelReferenceValues, RealTimeValue}
import com.convergencelabs.convergence.server.domain.model.ot.xform.IndexTransformer

/**
 * Represents and reference pointing to a set of indices, in a positionally
 * indexed data structure.
 *
 * @param target  The target of this reference, which is the object the
 *                reference is relative to.
 * @param session The session the created the reference.
 * @param key     The unique (within the target and session) key for
 *                this reference.
 */
class IndexReference(target: RealTimeValue,
                     session: DomainUserSessionId,
                     key: String)
  extends ModelReference[Int, RealTimeValue](target, session, key)
    with PositionalInsertAware
    with PositionalRemoveAware
    with PositionalReorderAware {

  def handlePositionalInsert(index: Int, length: Int): Unit = {
    this.values = IndexTransformer.handleInsert(this.values, index, length)
  }

  def handlePositionalRemove(index: Int, length: Int): Unit = {
    this.values = IndexTransformer.handleRemove(this.values, index, length)
  }

  def handlePositionalReorder(fromIndex: Int, toIndex: Int): Unit = {
    this.values = IndexTransformer.handleReorder(this.values, fromIndex, toIndex)
  }

  override def toReferenceValues: IndexReferenceValues = IndexReferenceValues(get())
}
