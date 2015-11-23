package com.convergencelabs.server.datastore.domain.mapper

import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.domain.TokenPublicKey
import scala.language.implicitConversions

object TokenPublicKeyMapper {

  import TokenPublicKeyFields._

  private[domain] implicit class TokenPublicKeyToODocument(val tokenPublicKey: TokenPublicKey) extends AnyVal {
    def asODocument: ODocument = tokenPublicKeyToODocument(tokenPublicKey)
  }

  private[domain] implicit def tokenPublicKeyToODocument(tokenPublicKey: TokenPublicKey): ODocument = {
    val doc = new ODocument(TokenPublicKeyClassName)
    doc.field(KeyId, tokenPublicKey.id)
    doc.field(KeyName, tokenPublicKey.name)
    doc.field(KeyDescription, tokenPublicKey.description)
    doc.field(KeyDate, tokenPublicKey.keyDate)
    doc.field(Key, tokenPublicKey.key)
    doc.field(KeyEnabled, tokenPublicKey.enabled)
    doc
  }

  private[domain] implicit class ODocumentToTokenPublicKey(val d: ODocument) extends AnyVal {
    def asTokenPublicKey: TokenPublicKey = oDocumentToTokenPublicKey(d)
  }

  private[domain] def oDocumentToTokenPublicKey(doc: ODocument): TokenPublicKey = {
    if (doc.getClassName != TokenPublicKeyClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${TokenPublicKeyClassName}': ${doc.getClassName}")
    }
    TokenPublicKey(
      doc.field(KeyId),
      doc.field(KeyName),
      doc.field(KeyDescription),
      doc.field(KeyDate),
      doc.field(Key),
      doc.field(KeyEnabled))
  }

  private[domain] val TokenPublicKeyClassName = "TokenPublicKey"

  private[domain] object TokenPublicKeyFields {
    val KeyId = "id"
    val KeyName = "name"
    val KeyDescription = "description"
    val KeyDate = "keyDate"
    val Key = "key"
    val KeyEnabled = "enabled"
  }
}