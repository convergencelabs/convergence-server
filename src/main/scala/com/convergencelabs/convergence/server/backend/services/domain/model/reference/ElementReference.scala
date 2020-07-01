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

import com.convergencelabs.convergence.server.backend.services.domain.model.RealtimeModel
import com.convergencelabs.convergence.server.model.domain.model.ElementReferenceValues
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId

/**
 * Represents a reference that targets model elements.
 *
 * @param target  The target of this reference, which is the object the
 *                reference is relative to.
 * @param session The session the created the reference.
 * @param key     The unique (within the target and session) key for
 *                this reference.
 * @param initial The initial values to set.
 */
private[model] class ElementReference(target: RealtimeModel,
                                      session: DomainSessionAndUserId,
                                      key: String,
                                      initial: List[String])
  extends ModelReference[String, RealtimeModel](target, session, key, initial) {

  /**
   * Handles the case where an element was removed from the model. This
   * will remove the value from the reference if the reference contained
   * the element.
   *
   * @param valueId The value id of the element that was removed.
   */
  def handleElementDetached(valueId: String): Unit = {
    this.values = this.values filter (!_.equals(valueId))
  }

  override def referenceValues: ElementReferenceValues = ElementReferenceValues(get())
}
