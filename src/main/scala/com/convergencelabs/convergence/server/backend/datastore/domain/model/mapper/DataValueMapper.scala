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
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArrayValueMapper.{arrayValueToODocument, oDocumentToArrayValue, DocumentClassName => ArrayValueDocName, OpDocumentClassName => ArrayOpValueDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.BooleanValueMapper.{booleanValueToODocument, oDocumentToBooleanValue, DocumentClassName => BooleanValueDocName, OpDocumentClassName => BooleanOpValueDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DateValueMapper.{dateValueToODocument, oDocumentToDateValue, DocumentClassName => DateValueDocName, OpDocumentClassName => DateOpValueDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DoubleValueMapper.{doubleValueToODocument, oDocumentToDoubleValue, DocumentClassName => DoubleValueDocName, OpDocumentClassName => DoubleOpValueDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.NullValueMapper.{nullValueToODocument, oDocumentToNullValue, DocumentClassName => NullValueDocName, OpDocumentClassName => NullOpValueDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectValueMapper.{oDocumentToObjectValue, objectValueToODocument, DocumentClassName => ObjectValueDocName, OpDocumentClassName => ObjectOpValueDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.StringValueMapper.{oDocumentToStringValue, stringValueToODocument, DocumentClassName => StringValueDocName, OpDocumentClassName => StringOpValueDocName}
import com.convergencelabs.convergence.server.model.domain.model._
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.language.implicitConversions

object DataValueMapper extends ODocumentMapper {

  private[domain] implicit def dataValueToODocument(data: DataValue): ODocument = {
    data match {
      case data: ObjectValue => objectValueToODocument(data)
      case data: ArrayValue => arrayValueToODocument(data)
      case data: StringValue => stringValueToODocument(data)
      case data: BooleanValue => booleanValueToODocument(data)
      case data: DoubleValue => doubleValueToODocument(data)
      case data: NullValue => nullValueToODocument(data)
      case data: DateValue => dateValueToODocument(data)
    }
  }

  private[domain] implicit def oDocumentToDataValue(doc: ODocument): DataValue = {
    doc.getClassName match {
      case ObjectValueDocName | ObjectOpValueDocName => oDocumentToObjectValue(doc)
      case ArrayValueDocName | ArrayOpValueDocName => oDocumentToArrayValue(doc)
      case StringValueDocName | StringOpValueDocName => oDocumentToStringValue(doc)
      case BooleanValueDocName | BooleanOpValueDocName => oDocumentToBooleanValue(doc)
      case DoubleValueDocName | DoubleOpValueDocName => oDocumentToDoubleValue(doc)
      case NullValueDocName | NullOpValueDocName => oDocumentToNullValue(doc)
      case DateValueDocName | DateOpValueDocName => oDocumentToDateValue(doc)
    }
  }
}
