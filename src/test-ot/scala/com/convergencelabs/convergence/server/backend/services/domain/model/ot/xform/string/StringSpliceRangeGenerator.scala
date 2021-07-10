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

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.string

case class StringSpliceRange(index: Int, length: Int)

object StringSpliceRangeGenerator {
  def createDeleteRanges(modelValue: String): List[StringSpliceRange] = {
    val maxDeleteStart = modelValue.length
    (for {
      index <- 0 until maxDeleteStart
    } yield createRangesFromPositionToEnd(index, modelValue)).flatten.toList
  }

  private[this] def createRangesFromPositionToEnd(start: Int, modelValue: String): List[StringSpliceRange] = {
    val maxLength = modelValue.length - start
    val range = (0 until maxLength).toList
    for {
      r <- range
    } yield StringSpliceRange(start, r)
  }
}
