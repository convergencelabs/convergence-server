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

package com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper

import java.util.{List => JavaList}

import com.convergencelabs.convergence.server.backend.datastore.ODocumentMapper
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DataValueMapper._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedArraySetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.jdk.CollectionConverters._

object ArraySetOperationMapper extends ODocumentMapper {

  private[domain] def arraySetOperationToODocument(obj: AppliedArraySetOperation): ODocument = {
    val AppliedArraySetOperation(id, noOp, value, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    val docValue = value.map(dataValueToODocument)
    doc.field(Fields.Val, docValue.asJava)
    val oldValueDoc = oldValue map {_.map(dataValueToODocument)} map {_.asJava}
    doc.field(Fields.OldValue, oldValueDoc.orNull)
    doc
  }

  private[domain] def oDocumentToArraySetOperation(doc: ODocument): AppliedArraySetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[JavaList[ODocument]].asScala.toList.map {v => oDocumentToDataValue(v)}
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[JavaList[ODocument]]).map {_.asScala.toList.map(oDocumentToDataValue)}
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
