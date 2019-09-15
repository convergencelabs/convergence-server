package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.DomainUserSessionId

class ElementReference(
  source: Any,
  session: DomainUserSessionId,
  key: String)
    extends ModelReference[String](source, session, key) {

  def handleElementDetached(vid: String): Unit = {
    this.values filter(!_.equals(vid))
  }
}
