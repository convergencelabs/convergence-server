/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DateValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue

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
