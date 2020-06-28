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
import com.convergencelabs.convergence.server.domain.model.{PropertyReferenceValues, RealtimeValue}

class PropertyReference(target: RealtimeValue,
                        session: DomainUserSessionId,
                        key: String,
                        initialValues: List[String])
  extends ModelReference[String, RealtimeValue](target, session, key, initialValues) with PropertyRemoveAwareReference {

  def handlePropertyRemove(property: String): Unit = {
    this.values = this.values filter (_ != property)
  }

  override def referenceValues: PropertyReferenceValues = PropertyReferenceValues(get())
}
