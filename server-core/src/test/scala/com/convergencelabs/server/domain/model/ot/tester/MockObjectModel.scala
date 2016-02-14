package com.convergencelabs.server.domain.model.ot

import scala.collection.JavaConverters.mapAsScalaMapConverter

import com.convergencelabs.server.util.JValueMapper

class MockObjectModel(private var state: Map[String, Any]) extends MockModel {

  def updateModel(op: DiscreteOperation): Unit = {
    op match {
      case addProp: ObjectAddPropertyOperation => handleAddProperty(addProp)
      case setProp: ObjectSetPropertyOperation => handleSetPropery(setProp)
      case removeProp: ObjectRemovePropertyOperation => handleRemoveProperty(removeProp)
      case set: ObjectSetOperation => handleSet(set)
      case x: Any =>
        throw new IllegalArgumentException()
    }
  }

  private def handleAddProperty(op: ObjectAddPropertyOperation): Unit = {
    state = state + (op.property -> op.value)
  }

  private def handleSetPropery(op: ObjectSetPropertyOperation): Unit = {
    state = state + (op.property -> op.value)
  }

  private def handleRemoveProperty(op: ObjectRemovePropertyOperation): Unit = {
    state = state - op.property
  }

  private def handleSet(op: ObjectSetOperation): Unit = {
    state = op.value.values
  }

  def getData(): Map[String, Any] = {
    state
  }
}
