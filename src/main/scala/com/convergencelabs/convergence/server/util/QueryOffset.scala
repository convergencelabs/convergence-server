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

package com.convergencelabs.convergence.server.util

/**
 * A value class that wraps and optional integer representing an
 * offset into a set of ordered items such as a database query.
 *
 * @param value The offset, or None if no offset is set.
 */
class QueryOffset(val value: Option[Int]) extends AnyVal {
  def getOrZero: Int = value.getOrElse(0)
  def getOrElse(v: Int): QueryOffset = QueryOffset(value.getOrElse(v))
}

object QueryOffset {
  val Empty = new QueryOffset(None)
  def apply(): QueryOffset = Empty
  def apply(offset: Int): QueryOffset = new QueryOffset(Some(offset))
  def apply(offset: Option[Int]): QueryOffset = new QueryOffset(offset)
}
