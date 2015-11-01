package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import scala.collection.immutable.HashMap
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.db.record.OTrackedMap
import com.orientechnologies.orient.core.db.record.OTrackedSet
import java.util.Formatter.DateTime
import scala.collection.mutable.MutableList
import com.orientechnologies.orient.core.db.record.OTrackedList
import java.util.HashSet

object DomainConfigurationStore {
  // FIXME should all this stuff be private?

  val Domain = "Domain"

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

  def domainConfigToDocument(domainConfig: DomainConfig): ODocument = {
    val DomainConfig(id, DomainFqn(namespace, domainId), displayName, dbUsername, dbPassword, keys, TokenKeyPair(privateKey, publicKey)) = domainConfig

    val document = new ODocument()
    document.field(Id, id)
    document.field(Namespace, namespace)
    document.field(DomainId, domainId)
    document.field(DisplayName, displayName)
    document.field(DBUsername, dbUsername)
    document.field(DBPassword, dbPassword)

    val keyDocs = List()
    domainConfig.keys.values foreach { key => keyDocs add Map(KeyId -> key.id, KeyName -> key.name, KeyDescription -> key.description, KeyDate -> key.keyDate, Key -> key.key) }

    document.field(DomainConfigurationStore.Keys, keyDocs.asJava)

    val adminKeyPairDoc = Map(DomainConfigurationStore.PrivateKey -> privateKey, DomainConfigurationStore.PublicKey -> publicKey)
    document.field(DomainConfigurationStore.AdminKeyPair, adminKeyPairDoc.asJava)
    document
  }

  def documentToDomainConfig(doc: ODocument): DomainConfig = {
    val domainFqn = DomainFqn(doc.field(Namespace), doc.field(DomainId))
    val keyPairDoc: OTrackedMap[String] = doc.field(AdminKeyPair, OType.EMBEDDEDMAP)
    val keyPair = TokenKeyPair(keyPairDoc.get(PrivateKey), keyPairDoc.get(PublicKey))
    val domainConfig = DomainConfig(doc.field(Id),
      domainFqn, doc.field(DisplayName),
      doc.field(DBUsername),
      doc.field(DBPassword),
      documentToKeys(
        doc.field(Keys, OType.EMBEDDEDLIST)),
      keyPair)
    domainConfig
  }

  def documentToKeys(doc: java.util.List[OTrackedMap[Any]]): Map[String, TokenPublicKey] = {
    val keys = new HashMap[String, TokenPublicKey]
    doc.foreach { docKey => keys + docKey.get(KeyId).asInstanceOf[String] -> documentToTokenPublicKey(docKey.asInstanceOf[OTrackedMap[Any]]) }
    keys
  }

  def documentToTokenPublicKey(doc: OTrackedMap[Any]): TokenPublicKey = {
    TokenPublicKey(doc.get(KeyId).asInstanceOf[String], doc.get(KeyName).asInstanceOf[String], doc.get(KeyDescription).asInstanceOf[String], doc.get(KeyDate).asInstanceOf[Long], doc.get(Key).asInstanceOf[String], doc.get(KeyEnabled).asInstanceOf[Boolean])
  }
}

class DomainConfigurationStore(dbPool: OPartitionedDatabasePool) {

  def createDomainConfig(domainConfig: DomainConfig) = {
    val db = dbPool.acquire()
    db.save(DomainConfigurationStore.domainConfigToDocument(domainConfig), DomainConfigurationStore.Domain)
    db.close()
  }

  def domainExists(domainFqn: DomainFqn): Boolean = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT id FROM Domain WHERE namespace = :namespace and domainId = :domainId")
    val params: java.util.Map[String, String] = HashMap("namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    !result.isEmpty()
  }

  def getDomainConfig(domainFqn: DomainFqn): Option[DomainConfig] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE namespace = :namespace and domainId = :domainId")
    val params: java.util.Map[String, String] = HashMap("namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    result.asScala.toList match {
      case doc :: rest => Some(DomainConfigurationStore.documentToDomainConfig(doc))
      case Nil => None
    }
  }

  def getDomainConfig(id: String): Option[DomainConfig] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE id = :id")
    val params: java.util.Map[String, String] = HashMap("id" -> id)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    result.asScala.toList match {
      case doc :: rest => Some(DomainConfigurationStore.documentToDomainConfig(doc))
      case Nil => None
    }
  }

  def getDomainConfigsInNamespace(namespace: String): List[DomainConfig] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE namespace = :namespace")
    val params: java.util.Map[String, String] = HashMap("namespace" -> namespace)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    result.asScala.toList map { doc => DomainConfigurationStore.documentToDomainConfig(doc) }
  }

  def removeDomainConfig(id: String): Unit = {
    val db = dbPool.acquire()
    val command = new OCommandSQL("DELETE FROM Domain WHERE id = :id")
    val params = Map("id" -> id)
    db.command(command).execute(params)
    db.close()
  }

  def updateDomainConfig(newConfig: DomainConfig): Unit = {
    val db = dbPool.acquire()
    val updatedDoc = DomainConfigurationStore.domainConfigToDocument(newConfig)

    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Domain WHERE id = :id")
    val params: java.util.Map[String, String] = HashMap("id" -> newConfig.id)
    val result: java.util.List[ODocument] = db.command(query).execute(params)

    result.asScala.toList match {
      case doc :: rest => {
        doc.merge(updatedDoc, false, false)
        db.save(doc)
      }
      case Nil =>
    }
  }

  def getDomainKey(domainFqn: DomainFqn, keyId: String): Option[TokenPublicKey] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT keys[id = :keyId] FROM Domain WHERE namespace = :namespace and domainId = :domainId")
    val params: java.util.Map[String, String] = HashMap("namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId, "keyId" -> keyId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    result.asScala.toList match {
      case doc :: rest if (doc.field("keys").isInstanceOf[OTrackedMap[Any]]) =>
        Some(DomainConfigurationStore.documentToTokenPublicKey(doc.field("keys")))
      case _ => None
    }
  }

  def getDomainKeys(domainFqn: DomainFqn): Option[Map[String, TokenPublicKey]] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT keys FROM Domain WHERE namespace = :namespace and domainId = :domainId")
    val params: java.util.Map[String, String] = HashMap("namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    result.asScala.toList match {
      case doc :: rest => Some(DomainConfigurationStore.documentToKeys(doc.field(DomainConfigurationStore.Keys, OType.EMBEDDEDLIST)))
      case Nil => None
    }
  }

  //TODO: Add validation for if key exists
  def addDomainKey(fqn: DomainFqn, key: TokenPublicKey): Unit = ???
}