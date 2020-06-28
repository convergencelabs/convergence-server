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
import com.convergencelabs.convergence.server.domain.model.{ElementReferenceValues, RealTimeModel}

class ElementReference(
  target: RealTimeModel,
  session: DomainUserSessionId,
  key: String)
    extends ModelReference[String, RealTimeModel](target, session, key) {

  def handleElementDetached(vid: String): Unit = {
    this.values = this.values filter(!_.equals(vid))
  }

  override def toReferenceValues(): ElementReferenceValues = ElementReferenceValues(get())
}
