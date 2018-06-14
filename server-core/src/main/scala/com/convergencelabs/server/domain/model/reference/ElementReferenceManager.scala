package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.ClearReference
import com.convergencelabs.server.domain.model.ModelReferenceEvent
import com.convergencelabs.server.domain.model.PublishReference
import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.ReferenceType
import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.UnpublishReference
import ReferenceManager.ReferenceDoesNotExist
import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.RealTimeModel

object ElementReferenceManager {
  val ReferenceDoesNotExist = "Reference does not exist"
}

class ElementReferenceManager(
    private val source: RealTimeModel,
    private val validTypes: List[ReferenceType.Value]) {

  private[this] val rm = new ReferenceMap()
  
  def referenceMap(): ReferenceMap = rm

  def handleReferenceEvent(event: ModelReferenceEvent, sessionId: String): Unit = {
    event match {
      case publish: PublishReference => this.handleReferencePublished(publish, sessionId)
      case unpublish: UnpublishReference => this.handleReferenceUnpublished(unpublish, sessionId)
      case set: SetReference => this.handleReferenceSet(set, sessionId)
      case cleared: ClearReference => this.handleReferenceCleared(cleared, sessionId)
    }
  }

  def sessionDisconnected(sessionId: String): Unit = {
    this.rm.removeBySession(sessionId)
  }

  private[this] def handleReferencePublished(event: PublishReference, sessionId: String): Unit = {
    if (!this.validTypes.contains(event.referenceType)) {
      throw new IllegalArgumentException(s"Invalid reference type: ${event.referenceType}")
    }

    val reference = event.referenceType match {
      case ReferenceType.Element =>
        new ElementReference(this.source, sessionId, event.key)
    }

    this.referenceMap.put(reference)
  }

  private[this] def handleReferenceUnpublished(event: UnpublishReference, sessionId: String): Unit = {
    this.rm.remove(sessionId, event.key) match {
      case Some(reference) =>
      case None =>
        throw new IllegalArgumentException(ReferenceDoesNotExist)
    }
  }

  private[this] def handleReferenceCleared(event: ClearReference, sessionId: String): Unit = {
    this.rm.get(sessionId, event.key) match {
      case Some(reference) =>
        reference.clear()
      case None =>
        throw new IllegalArgumentException(ReferenceDoesNotExist)
    }
  }

  private[this] def handleReferenceSet(event: SetReference, sessionId: String): Unit = {
    this.rm.get(sessionId, event.key) match {
      case Some(reference: ElementReference) =>
        val vids = event.values.asInstanceOf[List[String]]
        vids filter { source.idToValue.contains(_) }
        
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
