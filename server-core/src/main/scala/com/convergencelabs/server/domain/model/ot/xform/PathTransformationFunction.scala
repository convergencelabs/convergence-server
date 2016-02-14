package com.convergencelabs.server.domain.model.ot

private[ot] trait PathTransformationFunction[A <: DiscreteOperation] {
  def transformDescendantPath(ancestor: A, descendantPath: List[_]): PathTransformation
}

private[ot] sealed trait PathTransformation
private[ot] case class PathUpdated(path: List[_]) extends PathTransformation
private[ot] case object PathObsoleted extends PathTransformation
private[ot] case object NoPathTransformation extends PathTransformation
