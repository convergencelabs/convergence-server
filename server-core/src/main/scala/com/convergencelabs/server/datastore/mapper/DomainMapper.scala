package com.convergencelabs.server.datastore.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.domain.DomainStatus

object DomainMapper extends ODocumentMapper {

  private[datastore] implicit class DomainUserToODocument(val domain: Domain) {
    def asODocument: ODocument = domainConfigToODocument(domain)
  }

  private[datastore] implicit def domainConfigToODocument(domainConfig: Domain): ODocument = {
    val Domain(
      id,
      DomainFqn(namespace, domainId),
      displayName,
      owner,
      status) = domainConfig

    val doc = new ODocument(DomainClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.Namespace, namespace)
    doc.field(Fields.DomainId, domainId)
    doc.field(Fields.DisplayName, displayName)
    doc.field(Fields.Status, status.toString())
    doc
  }

  private[datastore] implicit class ODocumentToDomain(val d: ODocument) {
    def asDomain: Domain = oDocumentToDomain(d)
  }

  private[datastore] implicit def oDocumentToDomain(doc: ODocument): Domain = {
    validateDocumentClass(doc, DomainClassName)
    val state: DomainStatus.Value = DomainStatus.withName(doc.field(Fields.Status))
    Domain(
      doc.field(Fields.Id),
      DomainFqn(doc.field(Fields.Namespace), doc.field(Fields.DomainId)),
      doc.field(Fields.DisplayName),
      doc.field(Fields.Owner).asInstanceOf[ODocument].field("uid"),
      state)
  }

  private[datastore] val DomainClassName = "Domain"

  private[datastore] object Fields {
    val Id = "id"
    val Namespace = "namespace"
    val DomainId = "domainId"
    val DisplayName = "displayName"
    val DBUsername = "dbUsername"
    val DBPassword = "dbPassword"
    val Owner = "owner"
    val Status = "status"
    val Uid = "uid"
  }
}
