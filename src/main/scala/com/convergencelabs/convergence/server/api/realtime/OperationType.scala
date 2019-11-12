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

package com.convergencelabs.convergence.server.api.realtime

object OperationType extends Enumeration {
  val Compound = 0
  val ArrayInsert = 1
  val ArrayReorder = 2
  val ArrayRemove = 3
  val ArraySet = 4
  val ArrayValue = 5
  val BooleanValue = 6
  val NumberAdd = 7
  val NumberValue = 8
  val ObjectAdd = 9
  val ObjectRemove = 10
  val ObjectSet = 11
  val ObjectValue = 12
  val StringInsert = 13
  val StringRemove = 14
  val StringValue = 15
  val DateValue = 16
}
