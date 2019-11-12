/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.util

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
