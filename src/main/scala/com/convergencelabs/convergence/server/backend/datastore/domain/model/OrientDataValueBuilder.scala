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

import java.util.Date

import com.convergencelabs.convergence.server.domain.model.data._
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.jdk.CollectionConverters._

object OrientDataValueBuilder {

  val Id = "id"
  val Model = "model"
  val Value = "value"

  def dataValueToODocument(value: DataValue, modelDoc: OIdentifiable): ODocument = {
    value match {
      case value: ObjectValue => objectValueToODocument(value, modelDoc)
      case value: ArrayValue => arrayValueToODocument(value, modelDoc)
      case value: StringValue => stringValueToODocument(value, modelDoc)
      case value: BooleanValue => booleanValueToODocument(value, modelDoc)
      case value: DoubleValue => doubleValueToODocument(value, modelDoc)
      case value: NullValue => nullValueToODocument(value, modelDoc)
      case value: DateValue => dateValueToODocument(value, modelDoc)
    }
  }

  def objectValueToODocument(value: ObjectValue, modelDoc: OIdentifiable): ODocument = {
    val objectDoc = new ODocument("ObjectValue")
    objectDoc.field(Model, modelDoc, OType.LINK)
    objectDoc.field(Id, value.id)
    val children = value.children map { case (k, v) => (k, dataValueToODocument(v, modelDoc)) }
    objectDoc.field("children", children.asJava, OType.LINKMAP)
    objectDoc
  }

  def arrayValueToODocument(value: ArrayValue, modelDoc: OIdentifiable): ODocument = {
    val arrayDoc = new ODocument("ArrayValue")
    arrayDoc.field(Model, modelDoc)
    arrayDoc.field(Id, value.id)
    val children = value.children map { child => dataValueToODocument(child, modelDoc) }
    arrayDoc.field("children", children.asJava, OType.LINKLIST)
    arrayDoc
  }

  def stringValueToODocument(value: StringValue, modelDoc: OIdentifiable): ODocument = {
    val stringDoc = new ODocument("StringValue")
    stringDoc.field(Model, modelDoc)
    stringDoc.field(Id, value.id)
    stringDoc.field(Value, value.value)
    stringDoc
  }

  def booleanValueToODocument(value: BooleanValue, modelDoc: OIdentifiable): ODocument = {
    val booleanValue = new ODocument("BooleanValue")
    booleanValue.field(Model, modelDoc)
    booleanValue.field(Id, value.id)
    booleanValue.field(Value, value.value)
    booleanValue
  }

  def doubleValueToODocument(value: DoubleValue, modelDoc: OIdentifiable): ODocument = {
    val doubleValue = new ODocument("DoubleValue")
    doubleValue.field(Model, modelDoc)
    doubleValue.field(Id, value.id)
    doubleValue.field(Value, value.value)
    doubleValue
  }

  def nullValueToODocument(value: NullValue, modelDoc: OIdentifiable): ODocument = {
    val nullValue = new ODocument("NullValue")
    nullValue.field(Model, modelDoc)
    nullValue.field(Id, value.id)
    nullValue
  }

  def dateValueToODocument(value: DateValue, modelDoc: OIdentifiable): ODocument = {
    val dateValue = new ODocument("DateValue")
    dateValue.field(Model, modelDoc)
    dateValue.field(Id, value.id)
    dateValue.field(Value, Date.from(value.value))
    dateValue
  }
}
