package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.DataValue
import ObjectValueMapper.ObjectValueToODocument
import ObjectValueMapper.{ DocumentClassName => ObjectValueDocName }
import ArrayValueMapper.{ DocumentClassName => ArrayValueDocName }
import StringValueMapper.{ DocumentClassName => StringValueDocName }
import BooleanValueMapper.{ DocumentClassName => BooleanValueDocName }
import DoubleValueMapper.{ DocumentClassName => DoubleValueDocName }
import NullValueMapper.{ DocumentClassName => NullValueDocName }
import ObjectValueMapper.ODocumentToObjectValue
import ArrayValueMapper.ODocumentToArrayValue
import StringValueMapper.ODocumentToStringValue
import BooleanValueMapper.ODocumentToBooleanValue
import DoubleValueMapper.ODocumentToDoubleValue
import NullValueMapper.ODocumentToNullValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DoubleValue

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
    }
  }

  private[domain] implicit class ODocumentToDataValue(val d: ODocument) extends AnyVal {
    def asDataValue: DataValue = oDocumentToDataValue(d)
  }

  private[domain] implicit def oDocumentToDataValue(doc: ODocument): DataValue = {
    doc.getClassName match {
      case ObjectValueDocName => doc.asObjectValue
      case ArrayValueDocName => doc.asArrayValue
      case StringValueDocName => doc.asStringValue
      case BooleanValueDocName => doc.asBooleanValue
      case DoubleValueDocName => doc.asDoubleValue
      case NullValueDocName => doc.asNullValue
    }
  }
}
