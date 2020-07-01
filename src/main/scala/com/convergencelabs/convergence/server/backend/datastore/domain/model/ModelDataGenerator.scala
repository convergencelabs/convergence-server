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

package com.convergencelabs.convergence.server.backend.datastore.domain.model

import java.time.Instant

import com.convergencelabs.convergence.server.model.domain.model._

object ModelDataGenerator {
  def apply(data: Map[String, Any]): ObjectValue = {
    val gen = new ModelDataGenerator()
    gen.create(data)
  }
}

final class ModelDataGenerator() {
  val ServerIdPrefix = "0:"
  var id: Int = 0

  def create(data: Map[String, Any]): ObjectValue = {
    map(data).asInstanceOf[ObjectValue]
  }

  private[this] def map(value: Any): DataValue = {
    value match {
      case obj: Map[Any, Any] @ unchecked =>
        if (obj.contains("$convergenceType")) {
          DateValue(nextId(), Instant.parse(obj.get("value").toString))
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
        DoubleValue(nextId(), num.doubleValue)
      case num: BigInt =>
        DoubleValue(nextId(), num.doubleValue)
      case num: Long =>
        DoubleValue(nextId(), num.doubleValue)
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