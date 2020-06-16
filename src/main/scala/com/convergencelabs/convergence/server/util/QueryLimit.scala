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
 * limit generally applied to a result set..
 *
 * @param value The limit, or None if no limit is set.
 */
class QueryLimit(val value: Option[Int]) extends AnyVal {
  def getOrElse(v: Int): QueryLimit = QueryLimit(value.getOrElse(v))
}

object QueryLimit {
  val Empty = new QueryLimit(None)
  def apply(): QueryLimit = Empty
  def apply(offset: Int): QueryLimit = new QueryLimit(Some(offset))
  def apply(offset: Option[Int]): QueryLimit = new QueryLimit(offset)
}