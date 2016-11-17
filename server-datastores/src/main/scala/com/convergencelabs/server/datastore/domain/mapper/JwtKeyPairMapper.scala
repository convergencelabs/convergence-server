package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.JwtKeyPair
import com.orientechnologies.orient.core.record.impl.ODocument

object JwtKeyPairMapper extends ODocumentMapper {

  private[domain] implicit class TokenKeyPairToODocument(val tokenPublicKey: JwtKeyPair) extends AnyVal {
    def asODocument: ODocument = tokenKeyPairToODocument(tokenPublicKey)
  }

  private[domain] implicit def tokenKeyPairToODocument(tokenKeyPair: JwtKeyPair): ODocument = {
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.PublicKey, tokenKeyPair.publicKey)
    doc.field(Fields.PrivateKey, tokenKeyPair.privateKey)
    doc
  }

  private[domain] implicit class ODocumentToTokenKeyPair(val d: ODocument) extends AnyVal {
    def asTokenKeyPair: JwtKeyPair = oDocumentToTokenKeyPair(d)
  }

  private[domain] def oDocumentToTokenKeyPair(doc: ODocument): JwtKeyPair = {
    validateDocumentClass(doc, DocumentClassName)

    JwtKeyPair(doc.field(Fields.PublicKey), doc.field(Fields.PrivateKey))
  }

  private[domain] val DocumentClassName = "TokenKeyPair"

  private[domain] object Fields {
    val PublicKey = "publicKey"
    val PrivateKey = "privateKey"
  }
}
