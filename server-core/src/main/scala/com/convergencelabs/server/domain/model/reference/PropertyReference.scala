package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer
import com.convergencelabs.server.domain.model.SessionKey

class PropertyReference(
  source: Any,
  session: SessionKey,
  key: String)
    extends ModelReference[String](source, session, key)
    with PropertyRemoveAware {

  def handlePropertyRemove(property: String): Unit = {
    this.values filter(!_.equals(property))
  }
}
