package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.Date
import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.UpdateSuccess
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStore.KeyInfo
import com.convergencelabs.server.domain.JwtAuthKey
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging

object JwtAuthKeyStore {
  val ClassName = "JwtAuthKey"

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

  val JwtAuthKeyClass = "JwtAuthKey"

  def getKeys(offset: Option[Int], limit: Option[Int]): Try[List[JwtAuthKey]] = tryWithDb { db =>
    val queryString = s"SELECT * FROM ${JwtAuthKeyClass} ORDER BY id ASC"
    val pageQuery = QueryUtil.buildPagedQuery(queryString, limit, offset)
    val query = new OSQLSynchQuery[ODocument](pageQuery)
    val result: JavaList[ODocument] = db.command(query).execute()
    result.asScala.toList map { JwtAuthKeyStore.docToJwtAuthKey(_) }
  }

  def getKey(id: String): Try[Option[JwtAuthKey]] = tryWithDb { db =>
    val queryString = s"SELECT * FROM ${JwtAuthKeyClass} WHERE id = :id"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(Id -> id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result) { JwtAuthKeyStore.docToJwtAuthKey(_) }
  }

  def createKey(key: KeyInfo): Try[CreateResult[Unit]] = {
    val KeyInfo(id, description, publicKey, enabled) = key
    val jwtAuthKey = JwtAuthKey(id, description, Instant.now(), publicKey, enabled)
    importKey(jwtAuthKey)
  }

  def importKey(jwtAuthKey: JwtAuthKey): Try[CreateResult[Unit]] = tryWithDb { db =>
    val doc = JwtAuthKeyStore.jwtAuthKeyToDoc(jwtAuthKey)
    db.save(doc)
    CreateSuccess(())
  } recover {
    case e: ORecordDuplicatedException => DuplicateValue
  }

  def updateKey(info: KeyInfo): Try[UpdateResult] = tryWithDb { db =>
    val KeyInfo(keyId, descr, key, enabled) = info
    val updateKey = JwtAuthKey(keyId, descr, Instant.now(), key, enabled)

    val updatedDoc = JwtAuthKeyStore.jwtAuthKeyToDoc(updateKey)
    val queryString = s"SELECT FROM ${JwtAuthKey} WHERE id = :id"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(Id -> keyId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) => {
        doc.merge(updatedDoc, false, false)
        db.save(doc)
        UpdateSuccess
      }
      case None => NotFound
    }
  }

  def deleteKey(id: String): Try[DeleteResult] = tryWithDb { db =>
    val queryString = s"DELETE FROM ${JwtAuthKey} WHERE id = :id"
    val command = new OCommandSQL(queryString)
    val params = Map(Id -> id)
    val deleted: Int = db.command(command).execute(params.asJava)
    deleted match {
      case 1 => DeleteSuccess
      case _ => NotFound
    }
  }
}
