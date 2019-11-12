/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import java.util.Date

object DateSetOperationMapper extends ODocumentMapper {

  private[domain] implicit class DateSetOperationToODocument(val s: AppliedDateSetOperation) extends AnyVal {
    def asODocument: ODocument = dateSetOperationToODocument(s)
  }

  private[domain] implicit def dateSetOperationToODocument(obj: AppliedDateSetOperation): ODocument = {
    val AppliedDateSetOperation(id, noOp, value, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Val, Date.from(value))
    doc.field(Fields.OldValue, oldValue.map(Date.from(_)).getOrElse(null))
    doc
  }

  private[domain] implicit class ODocumentToDateSetOperation(val d: ODocument) extends AnyVal {
    def asDateSetOperation: AppliedDateSetOperation = oDocumentToDateSetOperation(d)
  }

  private[domain] implicit def oDocumentToDateSetOperation(doc: ODocument): AppliedDateSetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[Date]
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[Date])
    AppliedDateSetOperation(id, noOp, value.toInstant(), oldValue map (_.toInstant()))
  }

  private[domain] val DocumentClassName = "DateSetOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Val = "val"
    val OldValue = "oldVal"
  }
}
