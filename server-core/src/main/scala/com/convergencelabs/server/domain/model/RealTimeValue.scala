package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.ot.AppliedDiscreteOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.reference.ModelReference
import com.convergencelabs.server.domain.model.reference.ReferenceManager

abstract class RealTimeValue(
  private[model] val id: String,
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

  def addDetachListener(listener: String => Unit) {
    listeners ::= listener
  }

  def removeDetachListener(listener: String => Unit) {
    listeners filter (!_.equals(listener))
  }

  def detach(): Unit = {
    listeners.foreach(_(id))
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

  def sessionDisconnected(sessionId: String): Unit = {
    this.referenceManager.sessionDisconnected(sessionId)
  }

  def processReferenceEvent(event: ModelReferenceEvent, sessionId: String): Try[Unit] = {
    if (this.validReferenceTypes.isEmpty) {
      Failure(new IllegalArgumentException("This RealTimeValue does not allow references"))
    }

    this.referenceManager.handleReferenceEvent(event, sessionId)

    Success(())
  }
  
  protected def processValidatedOperation(operation: DiscreteOperation): Try[AppliedDiscreteOperation]
}
