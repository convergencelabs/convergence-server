/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.DomainUserSessionId

class ElementReference(
  source: Any,
  session: DomainUserSessionId,
  key: String)
    extends ModelReference[String](source, session, key) {

  def handleElementDetached(vid: String): Unit = {
    this.values = this.values filter(!_.equals(vid))
  }
}
