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

import com.convergencelabs.convergence.server.backend.datastore.ODocumentMapper
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DataValueMapper._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedObjectSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import java.util.{Map => JavaMap}
import scala.jdk.CollectionConverters._

object ObjectSetOperationMapper extends ODocumentMapper {

  private[domain] def objectSetOperationToODocument(obj: AppliedObjectSetOperation): ODocument = {
    val AppliedObjectSetOperation(id, noOp, value, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    val valueDoc = value map {case (k, v) => (k, dataValueToODocument(v))}
    doc.field(Fields.Val, valueDoc.asJava)
    val oldValDoc = (oldValue map {_ map {case (k, v) => (k, dataValueToODocument(v))}}) map {_.asJava}
    doc.field(Fields.OldValue, oldValDoc.orNull)
    doc
  }

  private[domain] def oDocumentToObjectSetOperation(doc: ODocument): AppliedObjectSetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[JavaMap[String, ODocument]]
      .asScala.map {case (k, v) => (k, oDocumentToDataValue(v))}
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[JavaMap[String, ODocument]])
      .map {_.asScala.toMap map {case (k, v) => (k, oDocumentToDataValue(v))}}
    AppliedObjectSetOperation(id, noOp, value.toMap, oldValue)
  }

  private[domain] val DocumentClassName = "ObjectSetOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Val = "val"
    val OldValue = "oldVal"
  }
}
