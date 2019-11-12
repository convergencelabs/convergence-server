/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot.xform

object IndexTransformer {
  def handleInsert(indices: List[Int], insertIndex: Int, length: Int): List[Int] = {
    indices map (index =>
      if (index >= insertIndex) {
        index + length
      } else {
        index
      })
  }

  def handleReorder(indices: List[Int], fromIndex: Int, toIndex: Int): List[Int] = {
    indices map (index =>
      if (index >= toIndex && index < fromIndex) {
        index + 1
      } else if (index >= fromIndex && index < toIndex) {
        index - 1
      } else {
        index
      })
  }

  def handleRemove(indices: List[Int], removeIndex: Int, length: Int): List[Int] = {
    indices map (index =>
      if (index > removeIndex) {
        index - Math.min(index - removeIndex, length)
      } else {
        index
      })
  }
}
