/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.array

case class ArrayMoveRange(fromIndex: Int, toIndex: Int)

object MoveRangeGenerator {
  def createRanges(length: Int): List[ArrayMoveRange] = {
    (for {
      r <- 0 until length
    } yield createRangesFromPositionToEnd(r, length)).flatten.toList
  }

  private[this] def createRangesFromPositionToEnd(start: Int, length: Int): List[ArrayMoveRange] = {
    var result = List[ArrayMoveRange]()
    for {
      r <- start until length
    } {
      result = result :+ ArrayMoveRange(start, r)
      if (r != start) {
        result = result :+ ArrayMoveRange(r, start)
      }
    }

    result
  }
}
