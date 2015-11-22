package com.convergencelabs.server.domain.model.ot

private[ot] trait PathTransformationFunction[A <: DiscreteOperation] {
  def transformDescendantPath(ancestor: A, descendantPath: List[_]): PathTrasformation
}

sealed trait PathTrasformation
case class PathUpdated(path: List[_]) extends PathTrasformation
case object PathObsoleted extends PathTrasformation
case object NoPathTranslation extends PathTrasformation