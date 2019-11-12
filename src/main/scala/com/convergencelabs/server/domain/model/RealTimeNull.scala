/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.ot.AppliedDiscreteOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation

class RealTimeNull(
  private[this] val value: NullValue,
  private[this] val parent: Option[RealTimeContainerValue],
  private[this] val parentField: Option[Any])
    extends RealTimeValue(value.id, parent, parentField, List()) {

  def data(): Null = {
    null  // scalastyle:ignore null
  }
  
  def dataValue(): NullValue = {
    value
  }

  protected def processValidatedOperation(op: DiscreteOperation): Try[AppliedDiscreteOperation] = {
    Failure(new IllegalArgumentException("Invalid operation type for RealTimeNull: " + op));
  }
}
