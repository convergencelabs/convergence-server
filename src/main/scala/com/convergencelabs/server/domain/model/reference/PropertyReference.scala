package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.DomainUserSessionId

class PropertyReference(
  source: Any,
  session: DomainUserSessionId,
  key: String)
    extends ModelReference[String](source, session, key)
    with PropertyRemoveAware {

  def handlePropertyRemove(property: String): Unit = {
    this.values filter(!_.equals(property))
  }
}
