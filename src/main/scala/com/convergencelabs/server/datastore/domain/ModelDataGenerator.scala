/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain

import java.time.Instant

import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DateValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue


object ModelDataGenerator {
  def apply(data: Map[String, Any]): ObjectValue = {
    val gen = new ModelDataGenerator()
    gen.create(data)
  }
}

class ModelDataGenerator() {
  val ServerIdPrefix = "0:";
  var id: Int = 0;

  def create(data: Map[String, Any]): ObjectValue = {
    map(data).asInstanceOf[ObjectValue]
  }

  private[this] def map(value: Any): DataValue = {
    value match {
      case obj: Map[Any, Any] @ unchecked =>
        if (obj.contains("$convergenceType")) {
          DateValue(nextId(), Instant.parse(obj.get("value").toString()))
        } else {
          val children = obj map {
            case (k, v) => (k.toString, this.map(v))
          }
          ObjectValue(nextId(), children)
        }
      case arr: List[_] =>
        val array = arr.map(v => this.map(v))
        ArrayValue(nextId(), array)
      case num: Double =>
        DoubleValue(nextId(), num)
      case num: Int =>
        DoubleValue(nextId(), num.doubleValue())
      case num: BigInt =>
        DoubleValue(nextId(), num.doubleValue())
      case num: Long =>
        DoubleValue(nextId(), num.doubleValue())
      case bool: Boolean =>
        BooleanValue(nextId(), bool)
      case str: String =>
        StringValue(nextId(), str)
      case date: Instant =>
        DateValue(nextId(), date)
      case null =>
        NullValue(nextId())
    }
  }

  private[this] def nextId(): String = {
    id = id + 1
    this.ServerIdPrefix + id
  }
}