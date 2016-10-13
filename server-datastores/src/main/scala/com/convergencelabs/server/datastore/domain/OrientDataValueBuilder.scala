package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.convergencelabs.server.domain.model.data.NullValue
import scala.collection.JavaConverters._

object OrientDataValueBuilder {

  val Vid = "vid"
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
    }
  }

  def objectValueToODocument(value: ObjectValue, modelDoc: OIdentifiable): ODocument = {
    val objectDoc = new ODocument("ObjectValue")
    objectDoc.field(Model, modelDoc)
    objectDoc.field(Vid, value.id)
    val children = value.children map { case (k, v) => (k, dataValueToODocument(v, modelDoc)) }
    objectDoc.field("children", children.asJava, OType.LINKMAP)
    objectDoc
  }

  def arrayValueToODocument(value: ArrayValue, modelDoc: OIdentifiable): ODocument = {
    val arrayDoc = new ODocument("ArrayValue")
    arrayDoc.field(Model, modelDoc)
    arrayDoc.field(Vid, value.id)
    val children = value.children map { child => dataValueToODocument(child, modelDoc) }
    arrayDoc.field("children", children.asJava, OType.LINKLIST)
    arrayDoc
  }

  def stringValueToODocument(value: StringValue, modelDoc: OIdentifiable): ODocument = {
    val stringDoc = new ODocument("StringValue")
    stringDoc.field(Model, modelDoc)
    stringDoc.field(Vid, value.id)
    stringDoc.field(Value, value.value)
    stringDoc
  }

  def booleanValueToODocument(value: BooleanValue, modelDoc: OIdentifiable): ODocument = {
    val booleanValue = new ODocument("BooleanValue")
    booleanValue.field(Model, modelDoc)
    booleanValue.field(Vid, value.id)
    booleanValue.field(Value, value.value)
    booleanValue
  }

  def doubleValueToODocument(value: DoubleValue, modelDoc: OIdentifiable): ODocument = {
    val doubleValue = new ODocument("DoubleValue")
    doubleValue.field(Model, modelDoc)
    doubleValue.field(Vid, value.id)
    doubleValue.field(Value, value.value)
    doubleValue
  }

  def nullValueToODocument(value: NullValue, modelDoc: OIdentifiable): ODocument = {
    val nullValue = new ODocument("NullValue")
    nullValue.field(Model, modelDoc)
    nullValue.field(Vid, value.id)
    nullValue
  }
}
