package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

class ElementReference(
  source: Any,
  sessionId: String,
  key: String)
    extends ModelReference[String](source, sessionId, key) {

  def handleElementDetached(vid: String): Unit = {
    this.values filter(!_.equals(vid))
  }
}
