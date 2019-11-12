/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.model.ot.xform

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
