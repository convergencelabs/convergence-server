package com.convergencelabs.server.domain.model.ot.ops

trait DiscreteOperation extends Operation {
  val path: List[Any]
  val noOp: Boolean

  def copyBuilder(): DiscreteOperation.Builder

  def isParentOf(other: DiscreteOperation) = PathComparator.isParentOf(path, other.path)
  def isAncestorOf(other: DiscreteOperation) = PathComparator.isAncestorOf(path, other.path)
  def isChildOf(other: DiscreteOperation) = PathComparator.isChildOf(path, other.path)
  def isDescendantOf(other: DiscreteOperation) = PathComparator.isDescendantOf(path, other.path)
  def isSiblingOf(other: DiscreteOperation) = PathComparator.areSiblings(path, other.path)
}

object DiscreteOperation {
  abstract class Builder(var path: List[Any], var noOp: scala.Boolean) {
    def build():DiscreteOperation
  }
}