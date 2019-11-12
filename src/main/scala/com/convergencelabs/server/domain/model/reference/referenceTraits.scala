/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.reference

trait PositionalInsertAware {
  def handlePositionalInsert(index: Int, length: Int): Unit
}

trait PositionalRemoveAware {
  def handlePositionalRemove(index: Int, length: Int): Unit
}

trait PositionalReorderAware {
  def handlePositionalReorder(fromIndex: Int, toIndex: Int): Unit
}

trait PropertyRemoveAware {
  def handlePropertyRemove(property: String): Unit
}
