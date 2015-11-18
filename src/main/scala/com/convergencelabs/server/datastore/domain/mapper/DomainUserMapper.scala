package com.convergencelabs.server.datastore.domain.mapper

import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.domain.DomainUser
import scala.language.implicitConversions

object DomainUserMapper {
  
  implicit class DomainUserToODocument(val s: DomainUser) {
    def asODocument: ODocument = domainUserToODocument(s)
  }
  
  implicit def domainUserToODocument(obj: DomainUser): ODocument = {
    val doc = new ODocument("User")
    doc.field(Fields.Uid, obj.uid)
    doc.field(Fields.Username, obj.username)
    doc.field(Fields.FirstName, obj.firstName)
    doc.field(Fields.LastName, obj.lastName)
    doc.field(Fields.Email, obj.email)
  }
  
  implicit class ODocumentToDomainUser(val d: ODocument) {
    def asDomainUser: DomainUser = oDocumentToDomainUser(d)
  }
  
  implicit def oDocumentToDomainUser(doc: ODocument): DomainUser = {
    DomainUser(
      doc.field(Fields.Uid),
      doc.field(Fields.Username),
      doc.field(Fields.FirstName),
      doc.field(Fields.LastName),
      doc.field(Fields.Email))

  }
  
  private[this] object Fields {
    val Uid = "uid"
    val Username = "username"
    val FirstName = "firstName"
    val LastName = "lastName"
    val Email = "email"
  }
}