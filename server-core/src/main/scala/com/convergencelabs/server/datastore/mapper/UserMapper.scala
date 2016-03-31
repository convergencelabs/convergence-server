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
    doc.field(Fields.Uid, obj.uid)
    doc.field(Fields.Username, obj.username)
    doc
  }

  private[datastore] implicit class ODocumentToUser(val d: ODocument) extends AnyVal {
    def asUser: User = oDocumentToUser(d)
  }

  private[datastore] implicit def oDocumentToUser(doc: ODocument): User = {
    validateDocumentClass(doc, DocumentClassName)

    User(
      doc.field(Fields.Uid),
      doc.field(Fields.Username))
  }

  private[datastore] val DocumentClassName = "User"

  private[datastore] object Fields {
    val Uid = "uid"
    val Username = "username"
  }
}
