package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

class PropertyReference(
  source: Any,
  sessionId: String,
  key: String)
    extends ModelReference[String](source, sessionId, key)
    with PropertyRemoveAware {

  def handlePropertyRemove(property: String): Unit = {
    this.values filter(!_.equals(property))
  }
}
