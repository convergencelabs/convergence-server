/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

import com.convergencelabs.server.domain.model.ReferenceValue

private[ot] trait ReferenceTransformationFunction[S <: DiscreteOperation] {
  def transform(s: S, setReference: ReferenceValue): Option[ReferenceValue]
}
