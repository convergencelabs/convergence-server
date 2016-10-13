package com.convergencelabs.server.datastore.mapper

import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.User
import scala.language.implicitConversions

object UserMapper extends ODocumentMapper {

  private[datastore] implicit class UserToODocument(val u: User) extends AnyVal {
    def asODocument: ODocument = userToODocument(u)
  }

  private[datastore] implicit def userToODocument(obj: User): ODocument = {
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Username, obj.username)
    doc.field(Fields.Email, obj.email)
    doc.field(Fields.FirstName, obj.firstName)
    doc.field(Fields.LastName, obj.lastName)
    doc
  }

  private[datastore] implicit class ODocumentToUser(val d: ODocument) extends AnyVal {
    def asUser: User = oDocumentToUser(d)
  }

  private[datastore] implicit def oDocumentToUser(doc: ODocument): User = {
    validateDocumentClass(doc, DocumentClassName)

    User(
      doc.field(Fields.Username),
      doc.field(Fields.Email),
      doc.field(Fields.FirstName),
      doc.field(Fields.LastName))
  }

  private[datastore] val DocumentClassName = "User"

  private[datastore] object Fields {
    val Username = "username"
    val Email = "email"
    val FirstName = "firstName"
    val LastName = "lastName"
  }
}
