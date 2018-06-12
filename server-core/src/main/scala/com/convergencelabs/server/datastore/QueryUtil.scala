package com.convergencelabs.server.datastore

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.mutable.Buffer
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.orientechnologies.orient.core.command.script.OCommandScript
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.index.OIndex
import com.orientechnologies.orient.core.record.OElement
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.executor.OResultSet
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import grizzled.slf4j.Logging

object QueryUtil extends Logging {
  private[this] val MultipleElementsMessage = "Only exepected one element in the result list, but more than one returned."

  def getFromIndex(indexName: String, key: Any, db: ODatabaseDocument): Option[ODocument] = {
    val index = db.getMetadata.getIndexManager.getIndex(indexName).asInstanceOf[OIndex[OIdentifiable]]
    val oid: OIdentifiable = index.get(key)
    Option(oid) map { _.getRecord.asInstanceOf[ODocument] }
  }

  def getRidFromIndex(indexName: String, key: Any, db: ODatabaseDocument): Try[ORID] = {
    Try {
      getOptionalRidFromIndex(indexName, key, db).getOrElse {
        throw EntityNotFoundException(entityId = Some(key))
      }
    }
  }

  def getOptionalRidFromIndex(indexName: String, key: Any, db: ODatabaseDocument): Option[ORID] = {
    val index = db.getMetadata.getIndexManager.getIndex(indexName).asInstanceOf[OIndex[OIdentifiable]]
    val oid: OIdentifiable = index.get(key)
    Option(oid) map { o => o.getIdentity }
  }

  def query(q: String, p: Map[String, Any], db: ODatabaseDocument): List[ODocument] = {
    val rs = db.command(q, p.asJava)
    this.resultSetToList(rs)
  }
  
  def resultSetToList(rs: OResultSet): List[ODocument] = {
    val docs = Buffer[ODocument]()
    while (rs.hasNext()) {
      docs += rs.next.toElement.asInstanceOf[ODocument]
    }
    
    rs.close();
    
    docs.toList
  }

  def hasResults(q: String, p: Map[String, Any], db: ODatabaseDocument): Boolean = {
    val query = new OSQLSynchQuery[ODocument](q)
    val resultSet =  db.command(q, p.asJava)
    !resultSetToList(resultSet).isEmpty
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
    this.enforceSingletonResultList(list.asScala.toList)
  }
  
  def enforceSingletonResultList[T](list: List[T]): Option[T] = {
    list match {
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

  def lookupMandatoryDocument(query: String, params: Map[String, Any], db: ODatabaseDocument): Try[ODocument] = {
    val rs = db.query(query, params.asJava)
    val resultList = resultSetToList(rs)
    QueryUtil.enforceSingleResult(resultList)
  }

  def lookupOptionalDocument(query: String, params: Map[String, Any], db: ODatabaseDocument): Option[ODocument] = {
    val rs = db.query(query, params.asJava)
    val resultList = resultSetToList(rs)
    QueryUtil.enforceSingletonResultList(resultList)
  }
  
  def updateSingleDoc(query: String, params: Map[String, Any], db: ODatabaseDocument): Try[Unit] = {
    val rs: OResultSet = db.command(query, params.asJava)
    val count = 1
    count match {
      case 0 =>
        Failure(EntityNotFoundException())
      case 1 =>
        Success(())
      case _ =>
        Failure(new IllegalStateException(MultipleElementsMessage))
    }
  }
  
  def updateSingleDocWithScript(query: String, params: Map[String, Any], db: ODatabaseDocument): Try[Unit] = {
    val q = new OCommandScript()
    val rs: OResultSet = db.command(query, params.asJava)
    val count = 1
    count match {
      case 0 =>
        Failure(EntityNotFoundException())
      case 1 =>
        Success(())
      case _ =>
        Failure(new IllegalStateException(MultipleElementsMessage))
    }
  }
  
  def command(query: String, params: Map[String, Any], db: ODatabaseDocument): Try[Int] = Try {
    val result: OResultSet = db.command(query, params.asJava)
    1
  }
  
  def commandScript(command: String, params: Map[String, Any], db: ODatabaseDocument): Try[Unit] = Try {
    db.execute("sql", command, params.asJava)
    ()
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
