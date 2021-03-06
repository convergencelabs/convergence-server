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
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedCompoundOperation
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.jdk.CollectionConverters._

object CompoundOperationMapper extends ODocumentMapper {

  private[domain] def compoundOperationToODocument(obj: AppliedCompoundOperation): ODocument = {
    val AppliedCompoundOperation(ops) = obj
    val doc = new ODocument(DocumentClassName)
    val opDocs = ops.map(OrientDBOperationMapper.operationToODocument)
    doc.field(Fields.Ops, opDocs.asJava, OType.EMBEDDEDLIST)
    doc
  }

  private[domain] def oDocumentToCompoundOperation(doc: ODocument): AppliedCompoundOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val opDocs: JavaList[ODocument] = doc.field(Fields.Ops, OType.EMBEDDEDLIST)
    val ops = opDocs.asScala.toList.map(OrientDBOperationMapper.oDocumentToDiscreteOperation)
    AppliedCompoundOperation(ops)
  }

  private[domain] val DocumentClassName = "CompoundOperation"

  private[domain] object Fields {
    val Ops = "ops"
  }

}
