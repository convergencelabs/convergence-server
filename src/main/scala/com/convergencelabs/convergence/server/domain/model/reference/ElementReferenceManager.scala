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
import com.convergencelabs.convergence.server.domain.model.ClearReference
import com.convergencelabs.convergence.server.domain.model.ModelReferenceEvent
import com.convergencelabs.convergence.server.domain.model.RealTimeModel
import com.convergencelabs.convergence.server.domain.model.ReferenceType
import com.convergencelabs.convergence.server.domain.model.SetReference
import com.convergencelabs.convergence.server.domain.model.ShareReference
import com.convergencelabs.convergence.server.domain.model.UnshareReference

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
