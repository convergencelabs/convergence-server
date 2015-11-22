package com.convergencelabs.server.datastore.domain.mapper

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.convergencelabs.server.domain.model.ot.ops._
import com.convergencelabs.server.frontend.realtime.proto._
import org.json4s.JValue
import org.json4s._
import org.json4s.JValue
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._
import org.json4s.JsonAST.JNumber
import java.util.{ HashMap, Map => JavaMap, List => JavaList }
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.metadata.schema.OType
import java.util.{List => JavaList}
import java.util.{Map => JavaMap}
import ArrayInsertOperationMapper._
import ArrayRemoveOperationMapper._
import ArrayReplaceOperationMapper._
import ArrayMoveOperationMapper._
import ArraySetOperationMapper._
import ObjectSetPropertyOperationMapper._
import ObjectAddPropertyOperationMapper._
import ObjectRemovePropertyOperationMapper._
import ObjectSetOperationMapper._
import NumberAddOperationMapper._
import NumberSetOperationMapper._
import StringInsertOperationMapper._
import StringRemoveOperationMapper._
import StringSetOperationMapper._
import CompoundOperationMapper._

object OrientDBOperationMapper {

  def oDocumentToOperation(opAsDoc: ODocument): Operation = {
    opAsDoc.getClassName match {
      case CompoundOperationClassName => opAsDoc.asCompoundOperation
      case _ => oDocumentToDiscreteOperation(opAsDoc)
    }
  }

  private[mapper] def oDocumentToDiscreteOperation(doc: ODocument): DiscreteOperation = {
    doc.getClassName match {
      case StringInsertOperationClassName => doc.asStringInsertOperation
      case StringRemoveOperationClassName => doc.asStringRemoveOperation
      case StringSetOperationClassName => doc.asStringSetOperation

      case ArrayInsertOperationClassName => doc.asArrayInsertOperation
      case ArrayRemoveOperationClassName => doc.asArrayRemoveOperation
      case ArrayReplaceOperationClassName => doc.asArrayReplaceOperation
      case ArrayMoveOperationClassName => doc.asArrayMoveOperation
      case ArraySetOperationClassName => doc.asArraySetOperation

      case ObjectAddPropertyOperationClassName => doc.asObjectAddPropertyOperation
      case ObjectSetPropertyOperationClassName => doc.asObjectSetPropertyOperation
      case ObjectRemovePropertyOperationClassName => doc.asObjectRemovePropertyOperation
      case ObjectSetOperationClassName => doc.asObjectSetOperation

      case NumberAddOperationClassName => doc.asNumberAddOperation
      case NumberSetOperationClassName => doc.asNumberSetOperation
    }
  }

  def operationToODocument(op: Operation): ODocument = {
    op match {
      case op: CompoundOperation => op.asODocument
      case op: DiscreteOperation => discreteOperationToODocument(op)
    }
  }

  private[this] def discreteOperationToODocument(op: DiscreteOperation): ODocument = {
    op match {
      // String Operations
      case op: StringInsertOperation => op.asODocument
      case op: StringRemoveOperation => op.asODocument
      case op: StringSetOperation => op.asODocument

      // Array Operations
      case op: ArrayInsertOperation => op.asODocument
      case op: ArrayRemoveOperation => op.asODocument
      case op: ArrayMoveOperation => op.asODocument
      case op: ArrayReplaceOperation => op.asODocument
      case op: ArraySetOperation => op.asODocument

      // Object Operations
      case op: ObjectSetPropertyOperation => op.asODocument
      case op: ObjectAddPropertyOperation => op.asODocument
      case op: ObjectRemovePropertyOperation => op.asODocument
      case op: ObjectSetOperation => op.asODocument

      // Number Operations
      case op: NumberAddOperation => op.asODocument
      case op: NumberSetOperation => op.asODocument
    }
  }
}