package com.convergencelabs.server.datastore.domain.mapper

import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.domain.TokenKeyPair
import scala.language.implicitConversions
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object TokenKeyPairMapper extends ODocumentMapper {

  private[domain] implicit class TokenKeyPairToODocument(val tokenPublicKey: TokenKeyPair) extends AnyVal {
    def asODocument: ODocument = tokenKeyPairToODocument(tokenPublicKey)
  }

  private[domain] implicit def tokenKeyPairToODocument(tokenKeyPair: TokenKeyPair): ODocument = {
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.PublicKey, tokenKeyPair.publicKey)
    doc.field(Fields.PrivateKey, tokenKeyPair.privateKey)
    doc
  }

  private[domain] implicit class ODocumentToTokenKeyPair(val d: ODocument) extends AnyVal {
    def asTokenKeyPair: TokenKeyPair = oDocumentToTokenKeyPair(d)
  }

  private[domain] def oDocumentToTokenKeyPair(doc: ODocument): TokenKeyPair = {
    validateDocumentClass(doc, DocumentClassName)

    TokenKeyPair(doc.field(Fields.PublicKey), doc.field(Fields.PrivateKey))
  }

  private[domain] val DocumentClassName = "TokenKeyPair"

  private[domain] object Fields {
    val PublicKey = "publicKey"
    val PrivateKey = "privateKey"
  }
}
