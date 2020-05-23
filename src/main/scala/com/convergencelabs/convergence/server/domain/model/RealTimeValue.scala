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

package com.convergencelabs.convergence.server.domain.model

import com.convergencelabs.convergence.server.domain.DomainUserSessionId
import com.convergencelabs.convergence.server.domain.model.data.DataValue
import com.convergencelabs.convergence.server.domain.model.ot.{AppliedDiscreteOperation, DiscreteOperation}
import com.convergencelabs.convergence.server.domain.model.reference.{ModelReference, ReferenceManager}

import scala.util.{Failure, Try}

abstract class RealTimeValue(private[model] val id: String,
                             private[model] var parent: Option[RealTimeContainerValue],
                             private[model] var parentField: Option[Any],
                             validReferenceTypes: List[ReferenceType.Value]) {

  protected val referenceManager = new ReferenceManager(this, validReferenceTypes)
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
      Failure(new IllegalArgumentException("Operation id does not match this RealTimeValues's id"))
    } else if (operation.noOp) {
      Failure(new IllegalArgumentException("Operation should not be a noop when calling processOperation"))
    } else {
      processValidatedOperation(operation)
    }
  }

  def references(): Set[ModelReference[_]] = {
    this.referenceManager.referenceMap().getAll()
  }

  def sessionDisconnected(session: DomainUserSessionId): Unit = {
    this.referenceManager.sessionDisconnected(session)
  }

  def processReferenceEvent(event: ModelReferenceEvent, session: DomainUserSessionId): Try[Unit] = {
    if (this.validReferenceTypes.isEmpty) {
      Failure(new IllegalArgumentException("This RealTimeValue does not allow references"))
    } else {
      this.referenceManager.handleReferenceEvent(event, session)
    }
  }

  protected def processValidatedOperation(operation: DiscreteOperation): Try[AppliedDiscreteOperation]
}
