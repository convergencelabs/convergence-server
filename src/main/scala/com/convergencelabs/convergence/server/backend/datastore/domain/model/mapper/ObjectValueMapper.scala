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
import com.convergencelabs.convergence.server.model.domain.model.ObjectValue
import com.orientechnologies.orient.core.id.ORecordId
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.executor.OResult

import java.util.{Map => JavaMap}
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

object ObjectValueMapper extends ODocumentMapper {

  private[domain] implicit class ObjectValueToODocument(val obj: ObjectValue) extends AnyVal {
    def asODocument: ODocument = objectValueToODocument(obj)
  }

  private[domain] implicit def objectValueToODocument(obj: ObjectValue): ODocument = {
    val ObjectValue(id, children) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.Id, id)
    val docChildren = children map { case (k, v) => (k, dataValueToODocument(v)) }
    doc.field(Fields.Children, docChildren.asJava)
    doc
  }

  private[domain] implicit class ODocumentToObjectValue(val d: ODocument) extends AnyVal {
    def asObjectValue: ObjectValue = oDocumentToObjectValue(d)
  }

  private[domain] implicit def oDocumentToObjectValue(doc: ODocument): ObjectValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)

    val id = doc.getProperty(Fields.Id).asInstanceOf[String]
    val children: JavaMap[String, Any] = doc.getProperty(Fields.Children)
    val dataValues = children.asScala map {
      case (k, v: OResult) =>
        (k, oDocumentToDataValue(v.toElement.asInstanceOf[ODocument]))
      case (k, v: ORecordId) =>
        (k, oDocumentToDataValue(v.getRecord.asInstanceOf[ODocument]))
      case (k, v) =>
        (k, oDocumentToDataValue(v.asInstanceOf[ODocument]))
    }

    ObjectValue(id, dataValues.toMap)
  }

  private[domain] val DocumentClassName = "ObjectValue"
  private[domain] val OpDocumentClassName = "ObjectOpValue"

  private[domain] object Fields {
    val Id = "id"
    val Children = "children"
  }
}
