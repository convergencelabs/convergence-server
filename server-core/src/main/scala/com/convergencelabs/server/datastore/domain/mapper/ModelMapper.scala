package com.convergencelabs.server.datastore.domain.mapper

import java.time.Instant
import java.util.Date
import java.util.{ Map => JavaMap }
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object ModelMapper extends ODocumentMapper {

  private[domain] implicit class ModelToODocument(val m: Model) extends AnyVal {
    def asODocument: ODocument = modelToODocument(m)
  }

  private[domain] implicit def modelToODocument(model: Model): ODocument = {
    val doc = new ODocument(DocumentClassName)
    val dataMap = JValueMapper.jValueToJava(model.data)
    val metaData = model.metaData
    doc.field(Fields.CollectionId, metaData.fqn.collectionId)
    doc.field(Fields.ModelId, metaData.fqn.modelId)
    doc.field(Fields.Version, metaData.version)
    doc.field(Fields.CreatedTime, Date.from(metaData.createdTime))
    doc.field(Fields.ModifiedTime, Date.from(metaData.modifiedTime))
    doc.field(Fields.Data, dataMap)
    doc
  }

  private[domain] implicit class ODocumentToModel(val d: ODocument) extends AnyVal {
    def asModel: Model = oDocumentToModel(d)
  }

  private[domain] implicit def oDocumentToModel(doc: ODocument): Model = {
    validateDocumentClass(doc, DocumentClassName)

    // FIXME this assumes every thing is an object.
    val modelData: JavaMap[String, Any] = doc.field("data", OType.EMBEDDEDMAP)
    Model(oDocumentToModelMetaData(doc), JValueMapper.javaToJValue(modelData))
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
