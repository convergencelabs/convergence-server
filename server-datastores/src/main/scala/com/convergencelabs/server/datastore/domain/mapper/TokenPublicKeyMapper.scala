package com.convergencelabs.server.datastore.domain.mapper

import java.time.Instant
import java.util.Date
import scala.language.implicitConversions
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.TokenPublicKey
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument

object TokenPublicKeyMapper extends ODocumentMapper {

  private[domain] implicit class TokenPublicKeyToODocument(val tokenPublicKey: TokenPublicKey) extends AnyVal {
    def asODocument: ODocument = tokenPublicKeyToODocument(tokenPublicKey)
  }

  private[domain] implicit def tokenPublicKeyToODocument(tokenPublicKey: TokenPublicKey): ODocument = {
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, tokenPublicKey.id)
    doc.field(Fields.Name, tokenPublicKey.name)
    doc.field(Fields.Description, tokenPublicKey.description)
    doc.field(Fields.Created, new Date(tokenPublicKey.created.toEpochMilli()))
    doc.field(Fields.Key, tokenPublicKey.key)
    doc.field(Fields.Enabled, tokenPublicKey.enabled)
    doc
  }

  private[domain] implicit class ODocumentToTokenPublicKey(val d: ODocument) extends AnyVal {
    def asTokenPublicKey: TokenPublicKey = oDocumentToTokenPublicKey(d)
  }

  private[domain] def oDocumentToTokenPublicKey(doc: ODocument): TokenPublicKey = {
    validateDocumentClass(doc, DocumentClassName)

    val createdDate: Date = doc.field(Fields.Created, OType.DATETIME)

    TokenPublicKey(
      doc.field(Fields.Id),
      doc.field(Fields.Name),
      doc.field(Fields.Description),
      Instant.ofEpochMilli(createdDate.getTime),
      doc.field(Fields.Key),
      doc.field(Fields.Enabled))
  }

  private[domain] val DocumentClassName = "TokenPublicKey"

  private[domain] object Fields {
    val Id = "id"
    val Name = "name"
    val Description = "description"
    val Created = "created"
    val Key = "key"
    val Enabled = "enabled"
  }
}
