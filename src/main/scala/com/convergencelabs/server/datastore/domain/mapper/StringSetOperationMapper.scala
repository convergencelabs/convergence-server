/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object StringSetOperationMapper extends ODocumentMapper {

  private[domain] implicit class StringSetOperationToODocument(val s: AppliedStringSetOperation) extends AnyVal {
    def asODocument: ODocument = stringSetOperationToODocument(s)
  }

  private[domain] implicit def stringSetOperationToODocument(obj: AppliedStringSetOperation): ODocument = {
    val AppliedStringSetOperation(id, noOp, value, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Val, value)
    doc.field(Fields.OldValue, oldValue.getOrElse(null))
    doc
  }

  private[domain] implicit class ODocumentToStringSetOperation(val d: ODocument) extends AnyVal {
    def asStringSetOperation: AppliedStringSetOperation = oDocumentToStringSetOperation(d)
  }

  private[domain] implicit def oDocumentToStringSetOperation(doc: ODocument): AppliedStringSetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[String]
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[String])
    AppliedStringSetOperation(id, noOp, value, oldValue)
  }

  private[domain] val DocumentClassName = "StringSetOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Val = "val"
    val OldValue = "oldVal"
  }
}
