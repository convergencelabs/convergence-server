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

package com.convergencelabs.convergence.server.domain.model

import com.convergencelabs.convergence.server.domain.model.data._

/**
 * A base trait that creates [[RealtimeValue]] instances from [[DataValue]]s.
 */
trait RealtimeValueFactory {
  /**
   * Creates a new [[RealtimeValue]] from a [[DataValue]].
   *
   * @param value       The data value to create the [[RealtimeValue]] from.
   * @param parent      The parent [[RealtimeValue]] or None if this value
   *                    will have no parent.
   * @param parentField The field this new value will occupy in its parent
   *                    or none if it does not have a parent.
   * @return The newly created [[RealtimeValue]]
   */
  def createValue(value: DataValue,
                  parent: Option[RealtimeContainerValue],
                  parentField: Option[Any]): RealtimeValue = {
    value match {
      case v: StringValue => new RealtimeString(v, parent, parentField)
      case v: DoubleValue => new RealtimeDouble(v, parent, parentField)
      case v: BooleanValue => new RealtimeBoolean(v, parent, parentField)
      case v: ObjectValue => new RealtimeObject(v, parent, parentField, this)
      case v: ArrayValue => new RealtimeArray(v, parent, parentField, this)
      case v: NullValue => new RealtimeNull(v, parent, parentField)
      case v: DateValue => new RealtimeDate(v, parent, parentField)
    }
  }
}
