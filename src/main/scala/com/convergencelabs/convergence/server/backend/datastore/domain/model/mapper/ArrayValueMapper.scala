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
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DataValueMapper.{DataValueToODocument, ODocumentToDataValue}
import com.convergencelabs.convergence.server.model.domain.model
import com.convergencelabs.convergence.server.model.domain.model.ArrayValue
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.executor.OResult

import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

object ArrayValueMapper extends ODocumentMapper {

  private[domain] implicit class ArrayValueToODocument(val obj: ArrayValue) extends AnyVal {
    def asODocument: ODocument = arrayValueToODocument(obj)
  }

  private[domain] implicit def arrayValueToODocument(obj: ArrayValue): ODocument = {
    val ArrayValue(id, children) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.Id, id)
    val docChildren = children map { v => v.asODocument }
    doc.field(Fields.Children, docChildren.asJava)
    doc
  }

  private[domain] implicit class ODocumentToArrayValue(val d: ODocument) extends AnyVal {
    def asArrayValue: ArrayValue = oDocumentToArrayValue(d)
  }

  private[domain] implicit def oDocumentToArrayValue(doc: ODocument): ArrayValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val children: JavaList[Any] = doc.field(Fields.Children);
    val dataValues = children.asScala map {
      case result: OResult =>
        result.toElement.asInstanceOf[ODocument].asDataValue
      case v =>
        v.asInstanceOf[ODocument].asDataValue
    }
    model.ArrayValue(id, dataValues.toList)
  }

  private[domain] val DocumentClassName = "ArrayValue"
  private[domain] val OpDocumentClassName = "ArrayOpValue"

  private[domain] object Fields {
    val Id = "id"
    val Children = "children"
  }
}
