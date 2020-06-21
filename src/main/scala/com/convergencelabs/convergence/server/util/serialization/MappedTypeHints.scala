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

package com.convergencelabs.convergence.server.util.serialization

import org.json4s.TypeHints

final case class MappedTypeHints(private val hintMap: Map[String, Class[_]]) extends TypeHints {
  private val reverseHintMap = hintMap map (_.swap)

  val hints: List[Class[_]] = hintMap.values.toList

  def hintFor(clazz: Class[_]): String = reverseHintMap(clazz)
  def classFor(hint: String): Option[Class[_]] = hintMap.get(hint)
}
