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

package com.convergencelabs.convergence.server.backend.services.domain.model.value

import com.convergencelabs.convergence.server.backend.services.domain.model.RealtimeModelActor.ModelReferenceEvent
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.{AppliedDiscreteOperation, DiscreteOperation}
import com.convergencelabs.convergence.server.backend.services.domain.model.reference.{ModelReference, ValueReferenceManager}
import com.convergencelabs.convergence.server.model.domain.model.{DataValue, ModelReferenceValues}
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId

import scala.util.{Failure, Try}

private[model] abstract class RealtimeValue(val id: String,
                                            private[model] var parent: Option[RealtimeContainerValue],
                                            private[model] var parentField: Option[Any],
                                            validReferenceValueClasses: List[Class[_ <: ModelReferenceValues]]) {

  protected val referenceManager = new ValueReferenceManager(this, validReferenceValueClasses)
  protected var listeners: List[String => Unit] = Nil

  def path(): List[Any] = {
    parent match {
      case None => List()
      case Some(p) => p.path() :+ parentField
    }
  }

  def addDetachListener(listener: String => Unit): Unit = {
    listeners ::= listener
  }

  def removeDetachListener(listener: String => Unit): Unit = {
    listeners filter (!_.equals(listener))
  }

  def detach(): Unit = {
    listeners.foreach(_ (id))
  }

  def data(): Any

  def dataValue(): DataValue

  def processOperation(operation: DiscreteOperation): Try[AppliedDiscreteOperation] = {
    if (operation.id != this.id) {
      Failure(new IllegalArgumentException("Operation id does not match this RealTimeValue's id"))
    } else if (operation.noOp) {
      Failure(new IllegalArgumentException("Operation should not be a noop when calling processOperation"))
    } else {
      processValidatedOperation(operation)
    }
  }

  def references(): Set[ModelReference[_, _]] = {
    this.referenceManager.referenceMap().getAll
  }

  def sessionDisconnected(session: DomainSessionAndUserId): Unit = {
    this.referenceManager.sessionDisconnected(session)
  }

  def processReferenceEvent(event: ModelReferenceEvent, session: DomainSessionAndUserId): Try[Unit] = {
    if (this.validReferenceValueClasses.isEmpty) {
      Failure(new IllegalArgumentException("This RealTimeValue does not allow references"))
    } else {
      this.referenceManager.handleReferenceEvent(event)
    }
  }

  protected def processValidatedOperation(operation: DiscreteOperation): Try[AppliedDiscreteOperation]
}
