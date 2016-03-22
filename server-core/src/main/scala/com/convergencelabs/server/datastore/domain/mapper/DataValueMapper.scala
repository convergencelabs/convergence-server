package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.DataValue
import ObjectValueMapper.ObjectValueToODocument
import ObjectValueMapper.{ DocumentClassName => ObjectValueDocName }
import ObjectValueMapper.ODocumentToObjectValue
import com.convergencelabs.server.domain.model.data.ObjectValue

object DataValueMapper extends ODocumentMapper {

  private[domain] implicit class DataValueToODocument(val value: DataValue) extends AnyVal {
    def asODocument: ODocument = dataValueToODocument(value)
  }

  private[domain] implicit def dataValueToODocument(data: DataValue): ODocument = {
    data match {
      case data: ObjectValue => data.asODocument
    }
  }

  private[domain] implicit class ODocumentToDataValue(val d: ODocument) extends AnyVal {
    def asDataValue: DataValue = oDocumentToDataValue(d)
  }

  private[domain] implicit def oDocumentToDataValue(doc: ODocument): DataValue = {
    doc.getClassName match {
      case ObjectValueDocName => doc.asObjectValue
    }
  }
}
