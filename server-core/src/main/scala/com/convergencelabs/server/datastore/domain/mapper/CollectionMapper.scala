package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ModelSnapshotConfigToODocument
import com.convergencelabs.server.datastore.domain.mapper.ModelSnapshotConfigMapper.ODocumentToModelSnapshotConfig
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.Collection
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.domain.ModelSnapshotConfig

object CollectionMapper extends ODocumentMapper {

  private[domain] implicit class CollectionToODocument(val c: Collection) extends AnyVal {
    def asODocument: ODocument = collectionToODocument(c)
  }

  private[domain] implicit def collectionToODocument(collection: Collection): ODocument = {
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.CollectionId, collection.id)
    doc.field(Fields.Name, collection.name)
    doc.field(Fields.OverrideSnapshotConfig, collection.overrideSnapshotConfig)
    if (collection.snapshotConfig.isDefined) {
      doc.field(Fields.SnapshotConfig, collection.snapshotConfig.get.asODocument)
    }
    doc
  }

  private[domain] implicit class ODocumentToCollection(val d: ODocument) extends AnyVal {
    def asCollection: Collection = oDocumentToCollection(d)
  }

  private[domain] implicit def oDocumentToCollection(doc: ODocument): Collection = {
    validateDocumentClass(doc, DocumentClassName)
    val snapshotConfig: ODocument = doc.field(Fields.SnapshotConfig, OType.EMBEDDED);

    Collection(
        doc.field(Fields.CollectionId),
        doc.field(Fields.Name),
        doc.field(Fields.OverrideSnapshotConfig),
        toOption(snapshotConfig).map { x => x.asModelSnapshotConfig })
  }

  private[domain] val DocumentClassName = "Collection"

  private[domain] object Fields {
    val CollectionId = "collectionId"
    val Name = "name"
    val OverrideSnapshotConfig = "overrideSnapshotConfig"
    val SnapshotConfig = "snapshotConfig"
  }
}
