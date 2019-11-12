/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.reference

import com.convergencelabs.server.domain.DomainUserSessionId

abstract class ModelReference[T](
    val modelValue: Any,
    val session: DomainUserSessionId,
    val key: String) {

  protected var values: List[T] = List()

  def clear(): Unit = {
    this.values = List()
  }

  def set(values: List[T]): Unit = {
    this.values = values
  }

  def get(): List[T] = {
    this.values
  }

  def isSet(): Boolean = {
    this.get().nonEmpty
  }

  def handleSet(): Unit = {
    clear()
  }
}
