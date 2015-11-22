package com.convergencelabs.server.datastore.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.record.impl.ODocument

object DomainMapper {

  import DomainFields._

  private[datastore] implicit class DomainUserToODocument(val domain: Domain) {
    def asODocument: ODocument = domainConfigToODocument(domain)
  }

  private[datastore] implicit def domainConfigToODocument(domainConfig: Domain): ODocument = {
    val Domain(
      id,
      DomainFqn(namespace, domainId),
      displayName,
      dbUsername,
      dbPassword) = domainConfig

    val doc = new ODocument(DomainClassName)
    doc.field(Id, id)
    doc.field(Namespace, namespace)
    doc.field(DomainId, domainId)
    doc.field(DisplayName, displayName)
    doc.field(DBUsername, dbUsername)
    doc.field(DBPassword, dbPassword)

    doc
  }

  private[datastore] implicit class ODocumentToDomain(val d: ODocument) {
    def asDomain: Domain = oDocumentToDomain(d)
  }

  private[datastore] implicit def oDocumentToDomain(doc: ODocument): Domain = {
    if (doc.getClassName != DomainClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${DomainClassName}': ${doc.getClassName}")
    }
    
    Domain(
      doc.field(Id),
      DomainFqn(doc.field(Namespace), doc.field(DomainId)),
      doc.field(DisplayName),
      doc.field(DBUsername),
      doc.field(DBPassword))
  }

  private[datastore] val DomainClassName = "Domain"
  
  private[datastore] object DomainFields {
    val Id = "id"
    val Namespace = "namespace"
    val DomainId = "domainId"
    val DisplayName = "displayName"
    val DBUsername = "dbUsername"
    val DBPassword = "dbPassword"
  }
}