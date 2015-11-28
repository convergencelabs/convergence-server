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

object ModelMapper {

  import ModelFields._

  private[domain] implicit class ModelToODocument(val m: Model) extends AnyVal {
    def asODocument: ODocument = modelToODocument(m)
  }

  private[domain] implicit def modelToODocument(model: Model): ODocument = {
    val doc = new ODocument(ModelClassName)
    val dataMap = JValueMapper.jValueToJava(model.data)
    val metaData = model.metaData
    doc.field(CollectionId, metaData.fqn.collectionId)
    doc.field(ModelId, metaData.fqn.modelId)
    doc.field(Version, metaData.version)
    doc.field(CreatedTime, Date.from(metaData.createdTime))
    doc.field(ModifiedTime, Date.from(metaData.modifiedTime))
    doc.field(Data, dataMap)
  }

  private[domain] implicit class ODocumentToModel(val d: ODocument) extends AnyVal {
    def asModel: Model = oDocumentToModel(d)
  }

  private[domain] implicit def oDocumentToModel(doc: ODocument): Model = {
    if (doc.getClassName != ModelClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${ModelClassName}': ${doc.getClassName}")
    }

    // FIXME this assumes every thing is an object.
    val modelData: JavaMap[String, Any] = doc.field("data", OType.EMBEDDEDMAP)
    Model(oDocumentToModelMetaData(doc), JValueMapper.javaToJValue(modelData))
  }

  private[domain] implicit class ODocumentToModelMetaData(val d: ODocument) extends AnyVal {
    def asModelMetaData: ModelMetaData = oDocumentToModelMetaData(d)
  }

  private[domain] implicit def oDocumentToModelMetaData(doc: ODocument): ModelMetaData = {
    if (doc.getClassName != ModelClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${ModelClassName}': ${doc.getClassName}")
    }

    val createdTime: Date = doc.field(CreatedTime, OType.DATETIME)
    val modifiedTime: Date = doc.field(ModifiedTime, OType.DATETIME)

    ModelMetaData(
      ModelFqn(
        doc.field(CollectionId),
        doc.field(ModelId)),
      doc.field(Version, OType.LONG),
      createdTime.toInstant(),
      modifiedTime.toInstant())
  }

  private[domain] val ModelClassName = "Model"

  private[domain] object ModelFields {
    val CollectionId = "collectionId"
    val ModelId = "modeId"
    val Version = "version"
    val CreatedTime = "createdTime"
    val ModifiedTime = "modifiedTime"
    val Data = "data"
  }
}