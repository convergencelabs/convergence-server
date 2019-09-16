package com.convergencelabs.server.domain.model.ot

import com.convergencelabs.server.domain.model.ReferenceValue

private[ot] trait ReferenceTransformationFunction[S <: DiscreteOperation] {
  def transform(s: S, setReference: ReferenceValue): Option[ReferenceValue]
}
