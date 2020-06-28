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

import com.convergencelabs.convergence.server.datastore.domain.mapper.ArrayValueMapper.{ArrayValueToODocument, ODocumentToArrayValue, DocumentClassName => ArrayValueDocName, OpDocumentClassName => ArrayOpValueDocName}
import com.convergencelabs.convergence.server.datastore.domain.mapper.BooleanValueMapper.{BooleanValueToODocument, ODocumentToBooleanValue, DocumentClassName => BooleanValueDocName, OpDocumentClassName => BooleanOpValueDocName}
import com.convergencelabs.convergence.server.datastore.domain.mapper.DateValueMapper.{DateValueToODocument, ODocumentToDateValue, DocumentClassName => DateValueDocName, OpDocumentClassName => DateOpValueDocName}
import com.convergencelabs.convergence.server.datastore.domain.mapper.DoubleValueMapper.{DoubleValueToODocument, ODocumentToDoubleValue, DocumentClassName => DoubleValueDocName, OpDocumentClassName => DoubleOpValueDocName}
import com.convergencelabs.convergence.server.datastore.domain.mapper.NullValueMapper.{NullValueToODocument, ODocumentToNullValue, DocumentClassName => NullValueDocName, OpDocumentClassName => NullOpValueDocName}
import com.convergencelabs.convergence.server.datastore.domain.mapper.ObjectValueMapper.{ODocumentToObjectValue, ObjectValueToODocument, DocumentClassName => ObjectValueDocName, OpDocumentClassName => ObjectOpValueDocName}
import com.convergencelabs.convergence.server.datastore.domain.mapper.StringValueMapper.{ODocumentToStringValue, StringValueToODocument, DocumentClassName => StringValueDocName, OpDocumentClassName => StringOpValueDocName}
import com.convergencelabs.convergence.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.convergence.server.domain.model.data._
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.language.implicitConversions

object DataValueMapper extends ODocumentMapper {

  private[domain] implicit class DataValueToODocument(val value: DataValue) extends AnyVal {
    def asODocument: ODocument = dataValueToODocument(value)
  }

  private[domain] implicit def dataValueToODocument(data: DataValue): ODocument = {
    data match {
      case data: ObjectValue => data.asODocument
      case data: ArrayValue => data.asODocument
      case data: StringValue => data.asODocument
      case data: BooleanValue => data.asODocument
      case data: DoubleValue => data.asODocument
      case data: NullValue => data.asODocument
      case data: DateValue => data.asODocument
    }
  }

  private[domain] implicit class ODocumentToDataValue(val d: ODocument) extends AnyVal {
    def asDataValue: DataValue = oDocumentToDataValue(d)
  }

  private[domain] implicit def oDocumentToDataValue(doc: ODocument): DataValue = {
    doc.getClassName match {
      case ObjectValueDocName | ObjectOpValueDocName => doc.asObjectValue
      case ArrayValueDocName | ArrayOpValueDocName => doc.asArrayValue
      case StringValueDocName | StringOpValueDocName => doc.asStringValue
      case BooleanValueDocName | BooleanOpValueDocName => doc.asBooleanValue
      case DoubleValueDocName | DoubleOpValueDocName => doc.asDoubleValue
      case NullValueDocName | NullOpValueDocName => doc.asNullValue
      case DateValueDocName | DateOpValueDocName => doc.asDateValue
    }
  }
}
