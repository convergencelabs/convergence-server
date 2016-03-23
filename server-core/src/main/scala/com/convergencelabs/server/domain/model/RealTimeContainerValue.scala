package com.convergencelabs.server.domain.model

import scala.util.Try
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import scala.util.Failure
import scala.util.Success

abstract class RealTimeContainerValue(
  private[this] val id: String,
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeValue(id, model, parent, parentField) {

  def valueAt(path: List[Any]): Option[RealTimeValue]

  protected def child(childPath: Any): Try[Option[RealTimeValue]]
  
  override def detach(): Unit = {
    detachChildren()
    super.detach()
  }
  
  def detachChildren(): Unit
}
