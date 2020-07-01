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

package com.convergencelabs.convergence.server.backend.services.domain.model.reference

/**
 * Indicates that a reference can update its value based on a position
 * insert type of operation.
 */
trait PositionalInsertAwareReference {
  def handlePositionalInsert(index: Int, length: Int): Unit
}

/**
 * Indicates that a reference can update its value based on a position
 * remove type of operation.
 */
trait PositionalRemoveAwareReference {
  def handlePositionalRemove(index: Int, length: Int): Unit
}

/**
 * Indicates that a reference can update its value based on a position
 * reorder type of operation.
 */
trait PositionalReorderAwareReference {
  def handlePositionalReorder(fromIndex: Int, toIndex: Int): Unit
}

/**
 * Indicates that a reference can update its value based on a property
 * remove type of operation.
 */
trait PropertyRemoveAwareReference {
  def handlePropertyRemove(property: String): Unit
}
