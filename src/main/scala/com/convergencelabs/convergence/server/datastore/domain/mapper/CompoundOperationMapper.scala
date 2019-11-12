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

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions

import com.convergencelabs.convergence.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.convergence.server.domain.model.ot.AppliedCompoundOperation
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument

object CompoundOperationMapper extends ODocumentMapper {

  private[domain] implicit class CompoundOperationToODocument(val s: AppliedCompoundOperation) extends AnyVal {
    def asODocument: ODocument = compoundOperationToODocument(s)
  }

  private[domain] implicit def compoundOperationToODocument(obj: AppliedCompoundOperation): ODocument = {
    val AppliedCompoundOperation(ops) = obj
    val doc = new ODocument(DocumentClassName)
    val opDocs = ops.map { OrientDBOperationMapper.operationToODocument(_) }
    doc.field(Fields.Ops, opDocs.asJava, OType.EMBEDDEDLIST)
    doc
  }

  private[domain] implicit class ODocumentToCompoundOperation(val d: ODocument) extends AnyVal {
    def asCompoundOperation: AppliedCompoundOperation = oDocumentToCompoundOperation(d)
  }

  private[domain] implicit def oDocumentToCompoundOperation(doc: ODocument): AppliedCompoundOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val opDocs: JavaList[ODocument] = doc.field(Fields.Ops, OType.EMBEDDEDLIST)
    val ops = opDocs.asScala.toList.map { OrientDBOperationMapper.oDocumentToDiscreteOperation(_) }
    AppliedCompoundOperation(ops)
  }

  private[domain] val DocumentClassName = "CompoundOperation"

  private[domain] object Fields {
    val Ops = "ops"
  }
}
