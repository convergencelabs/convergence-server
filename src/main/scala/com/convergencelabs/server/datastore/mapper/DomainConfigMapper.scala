package com.convergencelabs.server.datastore.mapper

import com.convergencelabs.server.datastore.DomainConfig
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.TokenKeyPair
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.orientechnologies.orient.core.db.record.OTrackedMap
import com.orientechnologies.orient.core.metadata.schema.OType
import com.convergencelabs.server.domain.model.SnapshotConfig
import com.convergencelabs.server.datastore.mapper.SnapshotConfigMapper._
import java.util.{ List => JavaList }
import com.convergencelabs.server.datastore.TokenPublicKey
import scala.collection.immutable.HashMap
import scala.language.implicitConversions

object DomainConfigMapper {

  implicit class DomainUserToODocument(val domainConfig: DomainConfig) {
    def asODocument: ODocument = domainConfigToODocument(domainConfig)
  }

  implicit def domainConfigToODocument(domainConfig: DomainConfig): ODocument = {
    val DomainConfig(
      id,
      DomainFqn(namespace, domainId),
      displayName,
      dbUsername,
      dbPassword,
      keys,
      TokenKeyPair(privateKey, publicKey),
      snapshotConfig) = domainConfig

    val doc = new ODocument("Domain")
    doc.field(Fields.Id, id)
    doc.field(Fields.Namespace, namespace)
    doc.field(Fields.DomainId, domainId)
    doc.field(Fields.DisplayName, displayName)
    doc.field(Fields.DBUsername, dbUsername)
    doc.field(Fields.DBPassword, dbPassword)

    val keyDocs = List()
    domainConfig.keys.values foreach { key => keyDocs add Map(Fields.KeyId -> key.id, Fields.KeyName -> key.name, Fields.KeyDescription -> key.description, Fields.KeyDate -> key.keyDate, Fields.Key -> key.key) }

    doc.field(Fields.Keys, keyDocs.asJava)

    val adminKeyPairDoc = Map(Fields.PrivateKey -> privateKey, Fields.PublicKey -> publicKey)
    doc.field(Fields.AdminKeyPair, adminKeyPairDoc.asJava)
    doc.field(Fields.SnapshotConfig, snapshotConfig.asODocument)

    doc
  }

  implicit class ODocumentToDomainConfig(val d: ODocument) {
    def asDomainConfig: DomainConfig = oDocumentToDomainConfig(d)
  }

  implicit def oDocumentToDomainConfig(doc: ODocument): DomainConfig = {
    val domainFqn = DomainFqn(doc.field(Fields.Namespace), doc.field(Fields.DomainId))
    val keyPairDoc: OTrackedMap[String] = doc.field(Fields.AdminKeyPair, OType.EMBEDDEDMAP)
    val keyPair = TokenKeyPair(keyPairDoc.get(Fields.PrivateKey), keyPairDoc.get(Fields.PublicKey))

    val snapshotConfigDoc: ODocument = doc.field(Fields.SnapshotConfig)

    DomainConfig(
      doc.field(Fields.Id),
      domainFqn, doc.field(Fields.DisplayName),
      doc.field(Fields.DBUsername),
      doc.field(Fields.DBPassword),
      listToKeysMap(doc.field(Fields.Keys, OType.EMBEDDEDLIST)),
      keyPair,
      snapshotConfigDoc)

  }

  private[this] def listToKeysMap(doc: JavaList[OTrackedMap[Any]]): Map[String, TokenPublicKey] = {
    val keys = new HashMap[String, TokenPublicKey]
    doc.foreach { docKey =>
      keys + docKey.get(Fields.KeyId).asInstanceOf[String] -> mapToTokenPublicKey(docKey.asInstanceOf[OTrackedMap[Any]])
    }
    keys
  }

  private[this] def mapToTokenPublicKey(doc: OTrackedMap[Any]): TokenPublicKey = {
    TokenPublicKey(
      doc.get(Fields.KeyId).asInstanceOf[String],
      doc.get(Fields.KeyName).asInstanceOf[String],
      doc.get(Fields.KeyDescription).asInstanceOf[String],
      doc.get(Fields.KeyDate).asInstanceOf[Long],
      doc.get(Fields.Key).asInstanceOf[String],
      doc.get(Fields.KeyEnabled).asInstanceOf[Boolean])
  }

  private[this] object Fields {
    val Id = "id"
    val Namespace = "namespace"
    val DomainId = "domainId"
    val DisplayName = "displayName"

    val DBUsername = "dbUsername"
    val DBPassword = "dbPassword"

    val Keys = "keys"
    val KeyId = "id"
    val KeyName = "name"
    val KeyDescription = "description"
    val KeyDate = "keyDate"
    val Key = "key"
    val KeyEnabled = "enabled"

    val AdminKeyPair = "adminUIKeyPair"
    val PrivateKey = "privateKey"
    val PublicKey = "publicKey"

    val SnapshotConfig = "snapshotConfig"
  }
}