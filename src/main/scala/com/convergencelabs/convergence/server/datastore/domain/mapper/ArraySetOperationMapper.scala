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

package com.convergencelabs.convergence.server.datastore.domain.mapper

import java.util.{ List => JavaList }

import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

import com.convergencelabs.convergence.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.convergence.server.domain.model.ot.AppliedArraySetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue

object ArraySetOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArraySetOperationToODocument(val s: AppliedArraySetOperation) extends AnyVal {
    def asODocument: ODocument = arraySetOperationToODocument(s)
  }

  private[domain] implicit def arraySetOperationToODocument(obj: AppliedArraySetOperation): ODocument = {
    val AppliedArraySetOperation(id, noOp, value, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    val docValue = value map(_.asODocument);
    doc.field(Fields.Val, docValue.asJava)
    val oldValueDoc = oldValue map {_ map {_.asODocument}} map {_.asJava};
    doc.field(Fields.OldValue, oldValueDoc.orNull)
    doc
  }

  private[domain] implicit class ODocumentToArraySetOperation(val d: ODocument) extends AnyVal {
    def asArraySetOperation: AppliedArraySetOperation = oDocumentToArraySetOperation(d)
  }

  private[domain] implicit def oDocumentToArraySetOperation(doc: ODocument): AppliedArraySetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[JavaList[ODocument]].asScala.toList.map {v => v.asDataValue}
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[JavaList[ODocument]]) map {_.asScala.toList.map {_.asDataValue}}
    AppliedArraySetOperation(id, noOp, value, oldValue)
  }

  private[domain] val DocumentClassName = "ArraySetOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Val = "val"
    val OldValue = "oldVal"
  }
}
