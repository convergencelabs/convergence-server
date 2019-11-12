/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.util

import org.json4s.TypeHints

case class MappedTypeHits(private val hintMap: Map[String, Class[_]]) extends TypeHints {
  private val reverseHintMap = hintMap map (_.swap)

  val hints: List[Class[_]] = hintMap.values.toList

  def hintFor(clazz: Class[_]): String = reverseHintMap(clazz)
  def classFor(hint: String): Option[Class[_]] = hintMap.get(hint)
}
