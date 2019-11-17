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

class RealTimeValueFactory {
   def createValue(
    value: DataValue,
    parent: Option[RealTimeContainerValue],
    parentField: Option[Any]): RealTimeValue = {
    value match {
      case v: StringValue => new RealTimeString(v, parent, parentField)
      case v: DoubleValue => new RealTimeDouble(v, parent, parentField)
      case v: BooleanValue => new RealTimeBoolean(v, parent, parentField)
      case v: ObjectValue => new RealTimeObject(v, parent, parentField, this)
      case v: ArrayValue => new RealTimeArray(v, parent, parentField, this)
      case v: NullValue => new RealTimeNull(v, parent, parentField)
      case v: DateValue => new RealTimeDate(v, parent, parentField)
      case _ => throw new IllegalArgumentException("Unsupported type: " + value)
    }
   }
}
