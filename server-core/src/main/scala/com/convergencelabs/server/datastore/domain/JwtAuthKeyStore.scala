package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.Date
import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.domain.JwtAuthKey
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import JwtAuthKeyStore.KeyInfo
import grizzled.slf4j.Logging

object JwtAuthKeyStore {
  val ClassName = "JwtAuthKey"
  
  val IdIndex = "JwtAuthKey.id"

  object Fields {
    val Id = "id"
    val Name = "name"
    val Description = "description"
    val Updated = "updated"
    val Key = "key"
    val Enabled = "enabled"
  }

  case class KeyInfo(id: String, description: String, key: String, enabled: Boolean)

  def jwtAuthKeyToDoc(jwtAuthKey: JwtAuthKey): ODocument = {
    val doc = new ODocument(ClassName)
    doc.field(Fields.Id, jwtAuthKey.id)
    doc.field(Fields.Description, jwtAuthKey.description)
    doc.field(Fields.Updated, new Date(jwtAuthKey.updated.toEpochMilli()))
    doc.field(Fields.Key, jwtAuthKey.key)
    doc.field(Fields.Enabled, jwtAuthKey.enabled)
    doc
  }

  def docToJwtAuthKey(doc: ODocument): JwtAuthKey = {
    val createdDate: Date = doc.field(Fields.Updated, OType.DATETIME)
    JwtAuthKey(
      doc.field(Fields.Id),
      doc.field(Fields.Description),
      Instant.ofEpochMilli(createdDate.getTime),
      doc.field(Fields.Key),
      doc.field(Fields.Enabled))
  }
}

class JwtAuthKeyStore private[datastore] (
  private[this] val dbProvider: DatabaseProvider)
    extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  val Id = "id"


  def getKeys(offset: Option[Int], limit: Option[Int]): Try[List[JwtAuthKey]] = tryWithDb { db =>
    val queryString = "SELECT * FROM JwtAuthKey ORDER BY id ASC"
    val pageQuery = QueryUtil.buildPagedQuery(queryString, limit, offset)
    val query = new OSQLSynchQuery[ODocument](pageQuery)
//    val result: JavaList[ODocument] = db.command(query).execute()
//    result.asScala.toList map { JwtAuthKeyStore.docToJwtAuthKey(_) }
    ???
  }

  def getKey(id: String): Try[Option[JwtAuthKey]] = tryWithDb { db =>
    val queryString = "SELECT * FROM JwtAuthKey WHERE id = :id"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(Id -> id)
//    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
//    QueryUtil.mapSingletonList(result) { JwtAuthKeyStore.docToJwtAuthKey(_) }
    ???
  }

  def createKey(key: KeyInfo): Try[Unit] = {
    val KeyInfo(id, description, publicKey, enabled) = key
    val jwtAuthKey = JwtAuthKey(id, description, Instant.now(), publicKey, enabled)
    importKey(jwtAuthKey)
  }

  def importKey(jwtAuthKey: JwtAuthKey): Try[Unit] = tryWithDb { db =>
    val doc = JwtAuthKeyStore.jwtAuthKeyToDoc(jwtAuthKey)
    db.save(doc)
    ()
  } recoverWith {
    case e: ORecordDuplicatedException => handleDuplicateValue(e)
  }

  def updateKey(info: KeyInfo): Try[Unit] = tryWithDb { db =>
    val KeyInfo(keyId, descr, key, enabled) = info
    val updateKey = JwtAuthKey(keyId, descr, Instant.now(), key, enabled)

    val updatedDoc = JwtAuthKeyStore.jwtAuthKeyToDoc(updateKey)
    val queryString = "SELECT FROM JwtAuthKey WHERE id = :id"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(Id -> keyId)
//    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
//
//    QueryUtil.enforceSingletonResultList(result) match {
//      case Some(doc) => {
//        doc.merge(updatedDoc, false, false)
//        db.save(doc)
//        ()
//      }
//      case None =>
//        throw EntityNotFoundException()
//    }
    ???
  }

  def deleteKey(id: String): Try[Unit] = tryWithDb { db =>
    val queryString = "DELETE FROM JwtAuthKey WHERE id = :id"
    val command = new OCommandSQL(queryString)
    val params = Map(Id -> id)
//    val deleted: Int = db.command(command).execute(params.asJava)
//    deleted match {
//      case 1 =>
//        ()
//      case _ => 
//        throw EntityNotFoundException()
//    }
    ???
  }

  private[this] def handleDuplicateValue[T](e: ORecordDuplicatedException): Try[T] = {
    e.getIndexName match {
      case JwtAuthKeyStore.IdIndex =>
        Failure(DuplicateValueException(JwtAuthKeyStore.Fields.Id))
      case _ =>
        Failure(e)
    }
  }
}
