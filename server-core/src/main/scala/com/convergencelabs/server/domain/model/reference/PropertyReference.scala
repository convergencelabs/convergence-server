package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

class PropertyReference(
  source: RealTimeValue,
  sessionId: String,
  key: String)
    extends ModelReference[String](source, sessionId, key)
    with PropertyRemoveAware {

  def handlePropertyRemove(property: String): Unit = {
    this.value = this.value match {
      case Some(v) if v.equals(property) => None
      case value: Any => value
    }
  }
}
