/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.model.ClearReference
import com.convergencelabs.server.domain.model.ModelReferenceEvent
import com.convergencelabs.server.domain.model.ShareReference
import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.ReferenceType
import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.UnshareReference
import ReferenceManager.ReferenceDoesNotExist
import com.convergencelabs.server.domain.model.RealTimeValue
import com.convergencelabs.server.domain.model.RealTimeValue
import scala.util.Try
import com.convergencelabs.server.domain.DomainUserSessionId

object ReferenceManager {
  val ReferenceDoesNotExist = "Reference does not exist"
}

class ReferenceManager(
    private val source: RealTimeValue,
    private val validTypes: List[ReferenceType.Value]) {

  private[this] val rm = new ReferenceMap()

  def referenceMap(): ReferenceMap = rm

  def handleReferenceEvent(event: ModelReferenceEvent, session: DomainUserSessionId): Try[Unit] = {
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

  private[this] def handleReferencePublished(event: ShareReference, session: DomainUserSessionId): Try[Unit] = Try {
    if (!this.validTypes.contains(event.referenceType)) {
      throw new IllegalArgumentException(s"Invalid reference type: ${event.referenceType}")
    }

    val reference = event.referenceType match {
      case ReferenceType.Index =>
        new IndexReference(this.source, session, event.key)
      case ReferenceType.Range =>
        new RangeReference(this.source, session, event.key)
      case ReferenceType.Property =>
        new PropertyReference(this.source, session, event.key)
    }

    this.referenceMap.put(reference)
  }

  private[this] def handleReferenceUnpublished(event: UnshareReference, session: DomainUserSessionId): Try[Unit] = Try {
    this.rm.remove(session, event.key) match {
      case Some(reference) =>
      case None =>
        throw new IllegalArgumentException(ReferenceDoesNotExist)
    }
  }

  private[this] def handleReferenceCleared(event: ClearReference, session: DomainUserSessionId): Try[Unit] = Try {
    this.rm.get(session, event.key) match {
      case Some(reference) =>
        reference.clear()
      case None =>
        throw new IllegalArgumentException(ReferenceDoesNotExist)
    }
  }

  private[this] def handleReferenceSet(event: SetReference, session: DomainUserSessionId): Try[Unit] = Try {
    this.rm.get(session, event.key) match {
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
