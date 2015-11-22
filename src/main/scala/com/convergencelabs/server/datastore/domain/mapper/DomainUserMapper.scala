package com.convergencelabs.server.datastore.domain.mapper

import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.domain.DomainUser
import scala.language.implicitConversions

object DomainUserMapper {
  
  import DomainUserFields._
  
  private[domain] implicit class DomainUserToODocument(val s: DomainUser) extends AnyVal {
    def asODocument: ODocument = domainUserToODocument(s)
  }
  
  private[domain] implicit def domainUserToODocument(obj: DomainUser): ODocument = {
    val doc = new ODocument(DomainUserClassName)
    doc.field(Uid, obj.uid)
    doc.field(Username, obj.username)
    doc.field(FirstName, obj.firstName)
    doc.field(LastName, obj.lastName)
    doc.field(Email, obj.email)
  }
  
  private[domain] implicit class ODocumentToDomainUser(val d: ODocument) extends AnyVal {
    def asDomainUser: DomainUser = oDocumentToDomainUser(d)
  }
  
  private[domain] implicit def oDocumentToDomainUser(doc: ODocument): DomainUser = {
    assert(doc.getClassName == DomainUserClassName)
    DomainUser(
      doc.field(Uid),
      doc.field(Username),
      doc.field(FirstName),
      doc.field(LastName),
      doc.field(Email))

  }
  
  private[domain] val DomainUserClassName = "User"
  
  private[domain] object DomainUserFields {
    val Uid = "uid"
    val Username = "username"
    val FirstName = "firstName"
    val LastName = "lastName"
    val Email = "email"
  }
}