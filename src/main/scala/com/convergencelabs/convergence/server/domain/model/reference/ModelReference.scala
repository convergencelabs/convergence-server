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

package com.convergencelabs.convergence.server.domain.model.reference

import com.convergencelabs.convergence.server.domain.DomainUserSessionId

abstract class ModelReference[T](
    val modelValue: Any,
    val session: DomainUserSessionId,
    val key: String) {

  protected var values: List[T] = List()

  def clear(): Unit = {
    this.values = List()
  }

  def set(values: List[T]): Unit = {
    this.values = values
  }

  def get(): List[T] = {
    this.values
  }

  def isSet(): Boolean = {
    this.get().nonEmpty
  }

  def handleSet(): Unit = {
    clear()
  }
}
