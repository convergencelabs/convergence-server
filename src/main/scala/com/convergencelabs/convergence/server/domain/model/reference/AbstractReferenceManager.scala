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
import com.convergencelabs.convergence.server.domain.model.RealtimeModelActor._

import scala.util.{Failure, Try}

object AbstractReferenceManager {
  val ReferenceDoesNotExist = "Reference does not exist"
}

abstract class AbstractReferenceManager[S](protected val source: S,
                                           protected val validValueClasses: List[Class[_]]) {

  protected val rm = new ReferenceMap()

  def referenceMap(): ReferenceMap = rm

  def handleReferenceEvent(event: ModelReferenceEvent, session: DomainUserSessionId): Try[Unit] = {
    event match {
      case share: ShareReference =>
        handleReferenceShared(share, session)
      case unshare: UnshareReference =>
        handleReferenceUnShared(unshare, session)
      case set: SetReference =>
        handleReferenceSet(set, session)
      case cleared: ClearReference =>
        handleReferenceCleared(cleared, session)
    }
  }

  def sessionDisconnected(session: DomainUserSessionId): Unit = {
    this.rm.removeBySession(session)
  }

  private[this] def handleReferenceUnShared(event: UnshareReference, session: DomainUserSessionId): Try[Unit] = Try {
    this.rm.remove(session, event.key) match {
      case Some(_) =>
      // No-op
      case None =>
        throw new IllegalArgumentException(AbstractReferenceManager.ReferenceDoesNotExist)
    }
  }

  private[this] def handleReferenceCleared(event: ClearReference, session: DomainUserSessionId): Try[Unit] = Try {
    this.rm.get(session, event.key) match {
      case Some(reference) =>
        reference.clear()
      case None =>
        throw new IllegalArgumentException(AbstractReferenceManager.ReferenceDoesNotExist)
    }
  }

  protected def handleReferenceSet(event: SetReference, session: DomainUserSessionId): Try[Unit] = {
    this.rm.get(session, event.key) match {
      case Some(reference) =>
        processReferenceSet(event, reference, session)
      case None =>
        Failure(new IllegalArgumentException(AbstractReferenceManager.ReferenceDoesNotExist))
    }
  }

  protected def handleReferenceShared(event: ShareReference, session: DomainUserSessionId): Try[Unit] = {
    if (!this.validValueClasses.contains(event.values.getClass)) {
      Failure(new IllegalArgumentException(s"Invalid value class: ${event.values.getClass}"))
    } else {
      if (rm.has(session, event.key)) {
        Failure(new IllegalArgumentException(s"Reference '${event.key}' already shared for session '$session'"))
      } else {
        processReferenceShared(event, session)
      }
    }
  }

  protected def processReferenceShared(event: ShareReference, session: DomainUserSessionId): Try[Unit]

  protected def processReferenceSet(event: SetReference, reference: ModelReference[_, _], session: DomainUserSessionId): Try[Unit]
}
