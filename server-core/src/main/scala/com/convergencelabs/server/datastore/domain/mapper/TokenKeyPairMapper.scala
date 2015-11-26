package com.convergencelabs.server.datastore.domain.mapper

import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.domain.TokenKeyPair
import scala.language.implicitConversions

object TokenKeyPairMapper {

  import TokenKeyPairFields._

  private[domain] implicit class TokenKeyPairToODocument(val tokenPublicKey: TokenKeyPair) extends AnyVal {
    def asODocument: ODocument = tokenKeyPairToODocument(tokenPublicKey)
  }

  private[domain] implicit def tokenKeyPairToODocument(tokenKeyPair: TokenKeyPair): ODocument = {
    val doc = new ODocument(TokenKeyPairClassName)
    doc.field(PublicKey, tokenKeyPair.publicKey)
    doc.field(PrivateKey, tokenKeyPair.privateKey)
    doc
  }

  private[domain] implicit class ODocumentToTokenKeyPair(val d: ODocument) extends AnyVal {
    def asTokenKeyPair: TokenKeyPair = oDocumentToTokenKeyPair(d)
  }

  private[domain] def oDocumentToTokenKeyPair(doc: ODocument): TokenKeyPair = {
    if (doc.getClassName != TokenKeyPairClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${TokenKeyPairClassName}': ${doc.getClassName}")
    }
    
    TokenKeyPair(doc.field(PublicKey), doc.field(PrivateKey))
  }

  private[domain] val TokenKeyPairClassName = "TokenKeyPair"
  
  private[domain] object TokenKeyPairFields {
    val PublicKey = "publicKey"
    val PrivateKey = "privateKey"
  }
}