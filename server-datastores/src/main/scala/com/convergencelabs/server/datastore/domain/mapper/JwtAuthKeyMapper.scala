package com.convergencelabs.server.datastore.domain.mapper

import java.time.Instant
import java.util.Date
import scala.language.implicitConversions
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.JwtAuthKey
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument

object JwtAuthKeyMapper extends ODocumentMapper {

  private[domain] implicit class JwtAuthKeyToODocument(val tokenPublicKey: JwtAuthKey) extends AnyVal {
    def asODocument(className: String): ODocument = jwtAuthKeyToODocument(tokenPublicKey, className)
  }

  private[domain] implicit def jwtAuthKeyToODocument(tokenPublicKey: JwtAuthKey, className: String): ODocument = {
    val doc = new ODocument(className)
    doc.field(Fields.Id, tokenPublicKey.id)
    doc.field(Fields.Description, tokenPublicKey.description)
    doc.field(Fields.Created, new Date(tokenPublicKey.updated.toEpochMilli()))
    doc.field(Fields.Key, tokenPublicKey.key)
    doc.field(Fields.Enabled, tokenPublicKey.enabled)
    doc
  }

  private[domain] implicit class ODocumentToJwtAuthKey(val d: ODocument) extends AnyVal {
    def asJwtAuthKey: JwtAuthKey = oDocumentToJwtAuthKey(d)
  }

  private[domain] def oDocumentToJwtAuthKey(doc: ODocument): JwtAuthKey = {
//    validateDocumentClass(doc, DocumentClassName)

    val createdDate: Date = doc.field(Fields.Created, OType.DATETIME)

    JwtAuthKey(
      doc.field(Fields.Id),
      doc.field(Fields.Description),
      Instant.ofEpochMilli(createdDate.getTime),
      doc.field(Fields.Key),
      doc.field(Fields.Enabled))
  }

  private[domain] val DocumentClassName = "JwtAuthKey"

  private[domain] object Fields {
    val Id = "id"
    val Name = "name"
    val Description = "description"
    val Created = "created"
    val Key = "key"
    val Enabled = "enabled"
  }
}
