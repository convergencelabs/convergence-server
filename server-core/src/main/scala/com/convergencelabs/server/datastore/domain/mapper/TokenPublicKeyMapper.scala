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
    doc.field(Id, tokenPublicKey.id)
    doc.field(Name, tokenPublicKey.name)
    doc.field(Description, tokenPublicKey.description)
    doc.field(Created, tokenPublicKey.keyDate)
    doc.field(Key, tokenPublicKey.key)
    doc.field(Enabled, tokenPublicKey.enabled)
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
      doc.field(Id),
      doc.field(Name),
      doc.field(Description),
      doc.field(Created),
      doc.field(Key),
      doc.field(Enabled))
  }

  private[domain] val TokenPublicKeyClassName = "TokenPublicKey"

  private[domain] object TokenPublicKeyFields {
    val Id = "id"
    val Name = "name"
    val Description = "description"
    val Created = "keyDate"
    val Key = "key"
    val Enabled = "enabled"
  }
}