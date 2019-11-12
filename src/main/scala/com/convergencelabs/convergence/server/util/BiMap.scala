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

object BiMap {
  def apply[K,V](tuples: (K, V)*): BiMap[K,V] = new BiMap(tuples.toMap)
}

case class BiMap[K, V](map: Map[K, V]) {
  def this(tuples: (K, V)*) = this(tuples.toMap)
  private[this] val reverseMap = map map (_.swap)
  require(map.size == reverseMap.size, "BiMap must be constructed with a 1-to-1 mapping from keys to values")
  def getValue(k: K): Option[V] = map.get(k)
  def getKey(v: V): Option[K] = reverseMap.get(v)
  val keys = map.keys
  val values = reverseMap.keys
}
