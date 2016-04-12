package com.convergencelabs.server.datastore.domain

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
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

  def createKey(key: TokenPublicKey): Try[Unit] = tryWithDb { db =>
    db.save(key.asODocument)
    ()
  }

  def updateKey(key: TokenPublicKey): Try[Unit] = tryWithDb { db =>
    val updatedDoc = key.asODocument
    val queryString = "SELECT FROM TokenPublicKey WHERE id = :id"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(Id -> key.id)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        doc.merge(updatedDoc, false, false)
        db.save(doc)
        ()
      case None =>
        throw new IllegalArgumentException("Key not found")
    }
  }

  def deleteKey(id: String): Try[Unit] = tryWithDb { db =>
    val queryString = "DELETE FROM TokenPublicKey WHERE id = :id"
    val command = new OCommandSQL(queryString)
    val params = Map(Id -> id)
    val deleted: Int = db.command(command).execute(params.asJava)
    deleted match {
      case 1 => ()
      case _ => throw new IllegalArgumentException("The key could not be deleted because it did not exist")
    }
  }
}
