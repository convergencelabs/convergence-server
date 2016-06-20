package com.convergencelabs.server.domain.model.ot

import com.convergencelabs.server.domain.model.SetReference

private[ot] trait ReferenceTransformationFunction[S <: DiscreteOperation] {
  def transform(s: S, setReferene: SetReference): SetReference
}
