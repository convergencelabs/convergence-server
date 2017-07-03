package com.convergencelabs.server.datastore

import java.util.{ List => JavaList }

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.index.OIndex
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import grizzled.slf4j.Logging
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.command.script.OCommandScript

object QueryUtil extends Logging {
  private[this] val MultipleElementsMessage = "Only exepected one element in the result list, but more than one returned."

  def getFromIndex(indexName: String, key: Any, db: ODatabaseDocumentTx): Option[ODocument] = {
    val index = db.getMetadata.getIndexManager.getIndex(indexName).asInstanceOf[OIndex[OIdentifiable]]
    val oid: OIdentifiable = index.get(key)
    Option(oid) map { _.getRecord.asInstanceOf[ODocument] }
  }

  def getRidFromIndex(indexName: String, key: Any, db: ODatabaseDocumentTx): Try[ORID] = {
    Try {
      getOptionalRidFromIndex(indexName, key, db).getOrElse {
        throw EntityNotFoundException(entityId = Some(key))
      }
    }
  }

  def getOptionalRidFromIndex(indexName: String, key: Any, db: ODatabaseDocumentTx): Option[ORID] = {
    val index = db.getMetadata.getIndexManager.getIndex(indexName).asInstanceOf[OIndex[OIdentifiable]]
    val oid: OIdentifiable = index.get(key)
    Option(oid) map { o => o.getIdentity }
  }

  def query(q: String, p: Map[String, Any], db: ODatabaseDocumentTx): List[ODocument] = {
    val query = new OSQLSynchQuery[ODocument](q)
    val result: JavaList[ODocument] = db.command(query).execute(p.asJava)
    result.toList
  }

  def hasResults(q: String, p: Map[String, Any], db: ODatabaseDocumentTx): Boolean = {
    val query = new OSQLSynchQuery[ODocument](q)
    val result: JavaList[ODocument] = db.command(query).execute(p.asJava)
    !result.isEmpty
  }

  def buildPagedQuery(baseQuery: String, limit: Option[Int], offset: Option[Int]): String = {
    val limitOffsetString = (limit, offset) match {
      case (None, None) => ""
      case (Some(lim), None) => s" LIMIT $lim"
      case (None, Some(off)) => s" SKIP $off"
      case (Some(lim), Some(off)) => s" SKIP $off LIMIT $lim"
    }

    baseQuery + limitOffsetString
  }

  def enforceSingletonResultList[T](list: JavaList[T]): Option[T] = {
    list.asScala.toList match {
      case first :: Nil =>
        Some(first)
      case first :: rest =>
        logger.error(MultipleElementsMessage)
        None
      case Nil =>
        None
    }
  }

  def enforceSingleResult[T](list: JavaList[T]): Try[T] = {
    enforceSingleResult(list.asScala.toList)
  }
  
  def enforceSingleResult[T](list: List[T]): Try[T] = {
    list match {
      case first :: Nil =>
        Success(first)
      case first :: rest =>
        logger.error(MultipleElementsMessage)
        Failure(new IllegalStateException(MultipleElementsMessage))
      case Nil =>
        Failure(EntityNotFoundException())
    }
  }

  def lookupMandatoryDocument(query: String, params: Map[String, Any], db: ODatabaseDocumentTx): Try[ODocument] = {
    val q = new OSQLSynchQuery[ODocument](query)
    val results = db.command(q).execute(params.asJava).asInstanceOf[JavaList[ODocument]]
    QueryUtil.enforceSingleResult(results)
  }

  def lookupOptionalDocument(query: String, params: Map[String, Any], db: ODatabaseDocumentTx): Option[ODocument] = {
    val q = new OSQLSynchQuery[ODocument](query)
    val results: JavaList[ODocument] = db.command(q).execute(params.asJava)
    QueryUtil.enforceSingletonResultList(results)
  }
  
  def updateSingleDoc(query: String, params: Map[String, Any], db: ODatabaseDocumentTx): Try[Unit] = {
    val q = new OCommandSQL(query)
    val count: Int = db.command(q).execute(params.asJava)
    count match {
      case 0 =>
        Failure(EntityNotFoundException())
      case 1 =>
        Success(())
      case _ =>
        Failure(new IllegalStateException(MultipleElementsMessage))
    }
  }
  
  def updateSingleDocWithScript(query: String, params: Map[String, Any], db: ODatabaseDocumentTx): Try[Unit] = {
    val q = new OCommandScript(query)
    val count: Int = db.command(q).execute(params.asJava)
    count match {
      case 0 =>
        Failure(EntityNotFoundException())
      case 1 =>
        Success(())
      case _ =>
        Failure(new IllegalStateException(MultipleElementsMessage))
    }
  }
  
  def command(query: String, params: Map[String, Any], db: ODatabaseDocumentTx): Try[Int] = Try {
    val q = new OCommandSQL(query)
    val count: Int = db.command(q).execute(params.asJava)
    count
  }
  
  def commandScript(command: String, params: Map[String, Any], db: ODatabaseDocumentTx): Try[Unit] = Try {
    val q = new OCommandScript("sql", command)
    db.command(q).execute(params.asJava)
  }

  def mapSingletonList[T, L](list: JavaList[L])(m: L => T): Option[T] = {
    list.asScala.toList match {
      case first :: Nil => Some(m(first))
      case first :: rest =>
        logger.error(MultipleElementsMessage)
        None
      case Nil => None
    }
  }

  def mapSingletonListToOption[T, L](list: JavaList[L])(m: L => Option[T]): Option[T] = {
    list.asScala.toList match {
      case first :: Nil => m(first)
      case first :: rest =>
        logger.error(MultipleElementsMessage)
        None
      case Nil => None
    }
  }
}
