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

package com.convergencelabs.convergence.server.backend.services.domain.model.reference

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.IndexTransformer
import com.convergencelabs.convergence.server.backend.services.domain.model.value.RealtimeValue
import com.convergencelabs.convergence.server.model.domain.model.RangeReferenceValues
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId

/**
 * Represents and reference pointing to a set of ranges, in a positionally
 * indexed data structure.
 *
 * @param target  The target of this reference, which is the object the
 *                reference is relative to.
 * @param session The session the created the reference.
 * @param key     The unique (within the target and session) key for
 *                this reference.
 * @param initial The initial values to set.
 */
private[model] class RangeReference(target: RealtimeValue,
                                    session: DomainSessionAndUserId,
                                    key: String,
                                    initial: List[RangeReference.Range])
  extends ModelReference[RangeReference.Range, RealtimeValue](target, session, key, initial)
    with PositionalInsertAwareReference
    with PositionalRemoveAwareReference
    with PositionalReorderAwareReference {

  def handlePositionalInsert(index: Int, length: Int): Unit = {
    val newValues = this.values.map { v =>
      val xFormed = IndexTransformer.handleInsert(List(v.from, v.to), index, length)
      RangeReference.Range(xFormed.head, xFormed.last)
    }
    this.values = newValues
  }

  def handlePositionalRemove(index: Int, length: Int): Unit = {
    this.values = this.values.map { v =>
      val xFormed = IndexTransformer.handleRemove(List(v.from, v.to), index, length)
      RangeReference.Range(xFormed.head, xFormed.last)
    }
  }

  def handlePositionalReorder(fromIndex: Int, toIndex: Int): Unit = {
    this.values = this.values.map { v =>
      val xFormed = IndexTransformer.handleReorder(List(v.from, v.to), fromIndex, toIndex)
      RangeReference.Range(xFormed.head, xFormed.last)
    }
  }

  override def referenceValues: RangeReferenceValues = RangeReferenceValues(get())
}

object RangeReference {
  final case class Range(from: Int, to: Int)
}
