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
import com.convergencelabs.server.domain.model.RealTimeValue
import scala.util.Try

object ReferenceManager {
  val ReferenceDoesNotExist = "Reference does not exist"
}

class ReferenceManager(
    private val source: RealTimeValue,
    private val validTypes: List[ReferenceType.Value]) {

  private[this] val rm = new ReferenceMap()

  def referenceMap(): ReferenceMap = rm

  def handleReferenceEvent(event: ModelReferenceEvent, sessionId: String): Try[Unit] = {
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

  private[this] def handleReferencePublished(event: PublishReference, sessionId: String): Try[Unit] = Try {
    if (!this.validTypes.contains(event.referenceType)) {
      throw new IllegalArgumentException(s"Invalid reference type: ${event.referenceType}")
    }

    val reference = event.referenceType match {
      case ReferenceType.Index =>
        new IndexReference(this.source, sessionId, event.key)
      case ReferenceType.Range =>
        new RangeReference(this.source, sessionId, event.key)
      case ReferenceType.Property =>
        new PropertyReference(this.source, sessionId, event.key)
    }

    this.referenceMap.put(reference)
  }

  private[this] def handleReferenceUnpublished(event: UnpublishReference, sessionId: String): Try[Unit] = Try {
    this.rm.remove(sessionId, event.key) match {
      case Some(reference) =>
      case None =>
        throw new IllegalArgumentException(ReferenceDoesNotExist)
    }
  }

  private[this] def handleReferenceCleared(event: ClearReference, sessionId: String): Try[Unit] = Try {
    this.rm.get(sessionId, event.key) match {
      case Some(reference) =>
        reference.clear()
      case None =>
        throw new IllegalArgumentException(ReferenceDoesNotExist)
    }
  }

  private[this] def handleReferenceSet(event: SetReference, sessionId: String): Try[Unit] = Try {
    this.rm.get(sessionId, event.key) match {
      case Some(reference: IndexReference) =>
        reference.set(event.values.asInstanceOf[List[Int]])
      case Some(reference: RangeReference) =>
        reference.set(event.values.asInstanceOf[List[(Int, Int)]])
      case Some(reference: PropertyReference) =>
        reference.set(event.values.asInstanceOf[List[String]])
      case Some(_) =>
        throw new IllegalArgumentException("Unknown reference type")
      case None =>
        throw new IllegalArgumentException(ReferenceDoesNotExist)
    }
  }
}
