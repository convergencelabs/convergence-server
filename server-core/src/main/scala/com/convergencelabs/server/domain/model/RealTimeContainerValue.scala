package com.convergencelabs.server.domain.model

abstract class RealTimeContainerValue(
  private[this] val model: RealTimeModel,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeValue(model, parent, parentField) {
  
}
