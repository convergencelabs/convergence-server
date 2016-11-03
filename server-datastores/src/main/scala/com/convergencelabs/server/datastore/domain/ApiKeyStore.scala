package com.convergencelabs.server.datastore.domain

import java.util.{ List => JavaList }
import scala.util.Try
import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.domain.TokenPublicKey
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import grizzled.slf4j.Logging
import mapper.TokenPublicKeyMapper.ODocumentToTokenPublicKey
import mapper.TokenPublicKeyMapper.TokenPublicKeyToODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.datastore.UpdateSuccess
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.DeleteSuccess
import java.util.{ List => JavaList }
import scala.collection.JavaConverters._
import java.time.Instant
import com.convergencelabs.server.datastore.domain.ApiKeyStore.CreateKey

object ApiKeyStore {
  case class CreateKey(id: String, description: String, key: String, enabled: Boolean)
}

class ApiKeyStore private[datastore] (
  private[this] val dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  val Id = "id"

  def getKeys(offset: Option[Int], limit: Option[Int]): Try[List[TokenPublicKey]] = tryWithDb { db =>
    val queryString = "SELECT * FROM TokenPublicKey ORDER BY id ASC"
    val pageQuery = QueryUtil.buildPagedQuery(queryString, limit, offset)
    val query = new OSQLSynchQuery[ODocument](pageQuery)
    val result: JavaList[ODocument] = db.command(query).execute()
    result.asScala.toList map { _.asTokenPublicKey }
  }

  def getKey(id: String): Try[Option[TokenPublicKey]] = tryWithDb { db =>
    val queryString = "SELECT * FROM TokenPublicKey WHERE id = :id"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(Id -> id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result) { _.asTokenPublicKey }
  }

  def createKey(key: CreateKey): Try[CreateResult[Unit]] = tryWithDb { db =>
    val CreateKey(id, descriptin, keyString, enabled) = key
    val tokenKey = TokenPublicKey(id, descriptin, Instant.now(), keyString, enabled)

    try {
      db.save(tokenKey.asODocument)
      CreateSuccess(())
    } catch {
      case e: ORecordDuplicatedException => DuplicateValue
    }
  }

  def updateKey(key: TokenPublicKey): Try[UpdateResult] = tryWithDb { db =>
    val updatedDoc = key.asODocument
    val queryString = "SELECT FROM TokenPublicKey WHERE id = :id"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(Id -> key.id)
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
    val queryString = "DELETE FROM TokenPublicKey WHERE id = :id"
    val command = new OCommandSQL(queryString)
    val params = Map(Id -> id)
    val deleted: Int = db.command(command).execute(params.asJava)
    deleted match {
      case 1 => DeleteSuccess
      case _ => NotFound
    }
  }
}
