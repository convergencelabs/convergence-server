package com.convergencelabs.server.datastore.domain.mapper

import java.util.Date
import java.util.{ Map => JavaMap }

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import ObjectValueMapper.ODocumentToObjectValue

object ModelMapper extends ODocumentMapper {

  private[domain] implicit class ODocumentToModel(val d: ODocument) extends AnyVal {
    def asModel: Model = oDocumentToModel(d)
  }

  private[domain] implicit def oDocumentToModel(doc: ODocument): Model = {
    validateDocumentClass(doc, DocumentClassName)
    val data: ODocument = doc.field("data");
    Model(oDocumentToModelMetaData(doc), data.asObjectValue)
  }

  private[domain] implicit class ODocumentToModelMetaData(val d: ODocument) extends AnyVal {
    def asModelMetaData: ModelMetaData = oDocumentToModelMetaData(d)
  }

  private[domain] implicit def oDocumentToModelMetaData(doc: ODocument): ModelMetaData = {
    val createdTime: Date = doc.field(Fields.CreatedTime, OType.DATETIME)
    val modifiedTime: Date = doc.field(Fields.ModifiedTime, OType.DATETIME)

    ModelMetaData(
      ModelFqn(
        doc.field(Fields.CollectionId),
        doc.field(Fields.ModelId)),
      doc.field(Fields.Version, OType.LONG),
      createdTime.toInstant(),
      modifiedTime.toInstant())
  }

  private[domain] val DocumentClassName = "Model"

  private[domain] object Fields {
    val CollectionId = "collectionId"
    val ModelId = "modelId"
    val Version = "version"
    val CreatedTime = "createdTime"
    val ModifiedTime = "modifiedTime"
    val Data = "data"
  }
}
