package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.ot.xform.IndexTransformer

class ElementReference(
  source: Any,
  session: SessionKey,
  key: String)
    extends ModelReference[String](source, session, key) {

  def handleElementDetached(vid: String): Unit = {
    this.values filter(!_.equals(vid))
  }
}
