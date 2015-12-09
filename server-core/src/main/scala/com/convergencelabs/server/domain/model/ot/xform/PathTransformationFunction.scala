package com.convergencelabs.server.domain.model.ot

private[ot] trait PathTransformationFunction[A <: DiscreteOperation] {
  def transformDescendantPath(ancestor: A, descendantPath: List[_]): PathTrasformation
}

private[ot] sealed trait PathTrasformation
private[ot] case class PathUpdated(path: List[_]) extends PathTrasformation
private[ot] case object PathObsoleted extends PathTrasformation
private[ot] case object NoPathTransformation extends PathTrasformation
