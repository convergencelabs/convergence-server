package com.convergencelabs.server.datastore.mapper

import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.TokenPublicKey

object TokenPublicKeyMapper {

  implicit class TokenPublicKeyToODocument(val tokenPublicKey: TokenPublicKey) {
    def asODocument: ODocument = tokenPublicKeyToODocument(tokenPublicKey)
  }

  def tokenPublicKeyToODocument(tokenPublicKey: TokenPublicKey): ODocument = {
    val doc = new ODocument("TokenPublicKey")
    doc.field(Fields.KeyId, tokenPublicKey.id)
    doc.field(Fields.KeyName, tokenPublicKey.name)
    doc.field(Fields.KeyDescription, tokenPublicKey.description)
    doc.field(Fields.KeyDate, tokenPublicKey.keyDate)
    doc.field(Fields.Key, tokenPublicKey.key)
    doc.field(Fields.KeyEnabled, tokenPublicKey.enabled)
    doc
  }

  implicit class ODocumentToDomainUser(val d: ODocument) {
    def asTokenPublicKey: TokenPublicKey = oDocumentToTokenPublicKey(d)
  }

  def oDocumentToTokenPublicKey(doc: ODocument): TokenPublicKey = {
    TokenPublicKey(
      doc.field(Fields.KeyId),
      doc.field(Fields.KeyName),
      doc.field(Fields.KeyDescription),
      doc.field(Fields.KeyDate),
      doc.field(Fields.Key),
      doc.field(Fields.KeyEnabled))

  }

  private[this] object Fields {
    val KeyId = "id"
    val KeyName = "name"
    val KeyDescription = "description"
    val KeyDate = "keyDate"
    val Key = "key"
    val KeyEnabled = "enabled"
  }
}