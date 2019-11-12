/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.DomainUserSessionId
import com.convergencelabs.server.domain.model.ClearReference
import com.convergencelabs.server.domain.model.ModelReferenceEvent
import com.convergencelabs.server.domain.model.RealTimeModel
import com.convergencelabs.server.domain.model.ReferenceType
import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.ShareReference
import com.convergencelabs.server.domain.model.UnshareReference

import ReferenceManager.ReferenceDoesNotExist

object ElementReferenceManager {
  val ReferenceDoesNotExist = "Reference does not exist"
}

class ElementReferenceManager(
    private val source: RealTimeModel,
    private val validTypes: List[ReferenceType.Value]) {

  private[this] val rm = new ReferenceMap()
  
  def referenceMap(): ReferenceMap = rm

  def handleReferenceEvent(event: ModelReferenceEvent, session: DomainUserSessionId): Unit = {
    event match {
      case publish: ShareReference => this.handleReferencePublished(publish, session)
      case unpublish: UnshareReference => this.handleReferenceUnpublished(unpublish, session)
      case set: SetReference => this.handleReferenceSet(set, session)
      case cleared: ClearReference => this.handleReferenceCleared(cleared, session)
    }
  }

  def sessionDisconnected(session: DomainUserSessionId): Unit = {
    this.rm.removeBySession(session)
  }

  private[this] def handleReferencePublished(event: ShareReference, session: DomainUserSessionId): Unit = {
    if (!this.validTypes.contains(event.referenceType)) {
      throw new IllegalArgumentException(s"Invalid reference type: ${event.referenceType}")
    }

    val reference = event.referenceType match {
      case ReferenceType.Element =>
        new ElementReference(this.source, session, event.key)
    }

    this.referenceMap.put(reference)
  }

  private[this] def handleReferenceUnpublished(event: UnshareReference, session: DomainUserSessionId): Unit = {
    this.rm.remove(session, event.key) match {
      case Some(reference) =>
      case None =>
        throw new IllegalArgumentException(ReferenceDoesNotExist)
    }
  }

  private[this] def handleReferenceCleared(event: ClearReference, session: DomainUserSessionId): Unit = {
    this.rm.get(session, event.key) match {
      case Some(reference) =>
        reference.clear()
      case None =>
        throw new IllegalArgumentException(ReferenceDoesNotExist)
    }
  }

  private[this] def handleReferenceSet(event: SetReference, session: DomainUserSessionId): Unit = {
    this.rm.get(session, event.key) match {
      case Some(reference: ElementReference) =>
        val vids = event.values.asInstanceOf[List[String]]
        vids filter source.idToValue.contains
        
        for (vid <- vids) {
          source.idToValue(vid).addDetachListener(reference.handleElementDetached)
        }
        
        reference.set(vids)
      case Some(_) =>
        throw new IllegalArgumentException("Unknown reference type")
      case None =>
        throw new IllegalArgumentException(ReferenceDoesNotExist)
    }
  }
}
