package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.orientechnologies.orient.core.metadata.schema.OType
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter

object OrientDataValueBuilder {
  def dataValueToODocument(value: DataValue, modelDoc: ODocument): ODocument = {
    value match {
      case value: ObjectValue => objectValueToODocument(value, modelDoc)
      case value: ArrayValue => arrayValueToODocument(value, modelDoc)
      case value: StringValue => stringValueToODocument(value, modelDoc)
      case value: BooleanValue => booleanValueToODocument(value, modelDoc)
      case value: DoubleValue => doubleValueToODocument(value, modelDoc)
    }
  }
  
  def objectValueToODocument(value: ObjectValue, modelDoc: ODocument): ODocument = {
    val objectDoc = new ODocument("ObjectValue")
    objectDoc.field("model", modelDoc)
    objectDoc.field("vid", value.id)
    val children = value.children map {case (k, v) => (k, dataValueToODocument(v, modelDoc))}
    objectDoc.field("children", children.asJava, OType.LINKMAP)
    objectDoc
  }
  
  def arrayValueToODocument(value: ArrayValue, modelDoc: ODocument): ODocument = {
    val arrayDoc = new ODocument("ArrayValue")
    arrayDoc.field("model", modelDoc)
    arrayDoc.field("vid", value.id)
    var children = value.children map {child => dataValueToODocument(child, modelDoc)}
    arrayDoc.field("children", children.asJava, OType.LINKLIST)
    arrayDoc
  }
  
  def stringValueToODocument(value: StringValue, modelDoc: ODocument): ODocument = {
    val stringDoc = new ODocument("StringValue")
    stringDoc.field("model", modelDoc)
    stringDoc.field("vid", value.id)
    stringDoc.field("value", value.value)
    stringDoc
  }
  
  def booleanValueToODocument(value: BooleanValue, modelDoc: ODocument): ODocument = {
    val booleanValue = new ODocument("BooleanValue")
    booleanValue.field("model", modelDoc)
    booleanValue.field("vid", value.id)
    booleanValue.field("value", value.value)
    booleanValue
  }
  
  def doubleValueToODocument(value: DoubleValue, modelDoc: ODocument): ODocument = {
    val doubleValue = new ODocument("DoubleValue")
    doubleValue.field("model", modelDoc)
    doubleValue.field("vid", value.id)
    doubleValue.field("value", value.value)
    doubleValue
  }
}
