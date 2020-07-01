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

package com.convergencelabs.convergence.server.backend.services.domain.model.reference

import com.convergencelabs.convergence.server.backend.services.domain.model.RealtimeModelActor._
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionId

import scala.util.{Failure, Try}

/**
 * The base class of reference managers that help manage model references.
 * This class will track which references exist and handle incoming reference
 * events to keep the references up to date.
 *
 * @param source The source that this manager is managing references for.
 * @tparam S The type of the source.
 */
private[reference] abstract class AbstractReferenceManager[S](protected val source: S) {

  /**
   * Stores the references for this reference manager.
   */
  protected val rm = new ReferenceMap()

  def referenceMap(): ReferenceMap = rm

  /**
   * Handles an incoming ModelReferenceEvent.
   *
   * @param event The event that occurred.
   * @return Success if the event was handled properly, a Failure otherwise.
   */
  def handleReferenceEvent(event: ModelReferenceEvent): Try[Unit] = {
    validateSource(event).flatMap { _ =>
      event match {
        case share: ShareReference =>
          handleReferenceShared(share)
        case unshare: UnShareReference =>
          handleReferenceUnShared(unshare)
        case set: SetReference =>
          handleReferenceSet(set)
        case cleared: ClearReference =>
          handleReferenceCleared(cleared)
      }
    }
  }

  /**
   * Processes the disconnection of a session by removing all references
   * owned by that session.
   *
   * @param sessionId The id of the session that disconnected.
   */
  def sessionDisconnected(sessionId: DomainSessionId): Unit = {
    this.rm.removeAllReferencesForSession(sessionId)
  }

  /**
   * Handles the [[UnShareReference]] event by removing the reference.
   *
   * @param event The event to process.
   * @return Success if the event was handled properly, a Failure otherwise.
   */
  private[this] def handleReferenceUnShared(event: UnShareReference): Try[Unit] = Try {
    this.rm.remove(event.session, event.key) match {
      case Some(_) =>
      // No-op
      case None =>
        throw new IllegalArgumentException(AbstractReferenceManager.ReferenceDoesNotExist)
    }
  }

  /**
   * Handles the [[ClearReference]] event by clearing the reference.
   *
   * @param event The event to process.
   * @return Success if the event was handled properly, a Failure otherwise.
   */
  private[this] def handleReferenceCleared(event: ClearReference): Try[Unit] = Try {
    this.rm.get(event.session, event.key) match {
      case Some(reference) =>
        reference.clear()
      case None =>
        throw new IllegalArgumentException(AbstractReferenceManager.ReferenceDoesNotExist)
    }
  }

  /**
   * Handles the [[SetReference]] event by updating the reference.
   *
   * @param event The event to process.
   * @return Success if the event was handled properly, a Failure otherwise.
   */
  private[this] def handleReferenceSet(event: SetReference): Try[Unit] = {
    this.rm.get(event.session, event.key) match {
      case Some(reference) =>
        processReferenceSet(event, reference)
      case None =>
        Failure(new IllegalArgumentException(AbstractReferenceManager.ReferenceDoesNotExist))
    }
  }

  /**
   * Handles the [[ShareReference]] event by adding the new reference.
   *
   * @param event The event to process.
   * @return Success if the event was handled properly, a Failure otherwise.
   */
  private[this] def handleReferenceShared(event: ShareReference): Try[Unit] = {
    if (rm.has(event.session, event.key)) {
      Failure(new IllegalArgumentException(s"Reference '${event.key}' already shared for session '$event.session'"))
    } else {
      processReferenceShared(event)
    }
  }

  /**
   * Processes the shared reference. Subclasses will implement how to actually
   * create the new reference and set its initial value.
   *
   * @param event The event to process.
   * @return Success if the event was processed properly, a Failure otherwise.
   */
  protected def processReferenceShared(event: ShareReference): Try[Unit]

  /**
   * Processes the set reference. Subclasses will implement how to actually
   * create update the reference value based on the event.
   *
   * @param event The event to process.
   * @return Success if the event was processed properly, a Failure otherwise.
   */
  protected def processReferenceSet(event: SetReference, reference: ModelReference[_, _]): Try[Unit]

  /**
   * Ensures that the event is from the source that this reference manager
   * has domain over.
   *
   * @param event The event to validate.
   * @return Success if the event is for the propper source, a Failure
   *         otherwise.
   */
  protected def validateSource(event: ModelReferenceEvent): Try[Unit]
}

private[reference] object AbstractReferenceManager {
  val ReferenceDoesNotExist = "Reference does not exist"
}
