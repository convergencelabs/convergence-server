package com.convergencelabs.server.datastore.domain.mapper

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.convergencelabs.server.domain.model.ot._
import com.convergencelabs.server.frontend.realtime.proto._
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
import ArrayInsertOperationMapper.{DocumentClassName => ArrayInsertDocName}
import ArrayInsertOperationMapper._
import ArrayRemoveOperationMapper.{DocumentClassName => ArrayRemoveDocName}
import ArrayRemoveOperationMapper._
import ArrayReplaceOperationMapper.{DocumentClassName => ArrayReplaceDocName}
import ArrayReplaceOperationMapper._
import ArrayMoveOperationMapper.{DocumentClassName => ArrayMoveDocName}
import ArrayMoveOperationMapper._
import ArraySetOperationMapper.{DocumentClassName => ArraySetDocName}
import ArraySetOperationMapper._
import ObjectSetPropertyOperationMapper.{DocumentClassName => ObjectSetPropertyDocName}
import ObjectSetPropertyOperationMapper._
import ObjectAddPropertyOperationMapper.{DocumentClassName => ObjectAddPropertyDocName}
import ObjectAddPropertyOperationMapper._
import ObjectRemovePropertyOperationMapper.{DocumentClassName => ObjectRemovePropertyDocName}
import ObjectRemovePropertyOperationMapper._
import ObjectSetOperationMapper.{DocumentClassName => ObjectSetDocName}
import ObjectSetOperationMapper._
import NumberAddOperationMapper.{DocumentClassName => NumberAddDocName}
import NumberAddOperationMapper._
import NumberSetOperationMapper.{DocumentClassName => NumberSetDocName}
import NumberSetOperationMapper._
import StringInsertOperationMapper.{DocumentClassName => StringInsertDocName}
import StringInsertOperationMapper._
import StringRemoveOperationMapper.{DocumentClassName => StringRemoveDocName}
import StringRemoveOperationMapper._
import StringSetOperationMapper.{DocumentClassName => StringSetDocName}
import StringSetOperationMapper._
import CompoundOperationMapper.{DocumentClassName => CompoundDocName}
import CompoundOperationMapper._
import com.convergencelabs.server.domain.model.ot._

object OrientDBOperationMapper {

  def oDocumentToOperation(opAsDoc: ODocument): Operation = {
    opAsDoc.getClassName match {
      case CompoundDocName => opAsDoc.asCompoundOperation
      case _ => oDocumentToDiscreteOperation(opAsDoc)
    }
  }

  private[mapper] def oDocumentToDiscreteOperation(doc: ODocument): DiscreteOperation = {
    doc.getClassName match {
      case StringInsertDocName => doc.asStringInsertOperation
      case StringRemoveDocName => doc.asStringRemoveOperation
      case StringSetDocName => doc.asStringSetOperation

      case ArrayInsertDocName => doc.asArrayInsertOperation
      case ArrayRemoveDocName => doc.asArrayRemoveOperation
      case ArrayReplaceDocName => doc.asArrayReplaceOperation
      case ArrayMoveDocName => doc.asArrayMoveOperation
      case ArraySetDocName => doc.asArraySetOperation

      case ObjectAddPropertyDocName => doc.asObjectAddPropertyOperation
      case ObjectSetPropertyDocName => doc.asObjectSetPropertyOperation
      case ObjectRemovePropertyDocName => doc.asObjectRemovePropertyOperation
      case ObjectSetDocName => doc.asObjectSetOperation

      case NumberAddDocName => doc.asNumberAddOperation
      case NumberSetDocName => doc.asNumberSetOperation
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