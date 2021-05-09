/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore

import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset, TryWithResource}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.{ORID, ORecordId}
import com.orientechnologies.orient.core.index.OCompositeKey
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.OElement
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.executor.OResultSet

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object OrientDBUtil {

  val CountField = "count"

  def query(db: ODatabaseDocument, query: String, params: Map[String, Any] = Map()): Try[List[ODocument]] = {
    TryWithResource(db.query(query, params.asJava))(resultSetToDocList)
  }

  def queryAndMap[T](db: ODatabaseDocument, query: String, params: Map[String, Any] = Map())(m: ODocument => T): Try[List[T]] = {
    this.query(db, query, params).map(_.map(m(_)))
  }

  /**
   * Executes a mutating command within the database and returns the number of
   * records that were modified. This methods assumes there is a single result
   * set produced by the command, and that the result set contains a 'count'
   * field indicating the number of mutated records.
   */
  def commandReturningCount(db: ODatabaseDocument, command: String, params: Map[String, Any] = Map()): Try[Long] = {
    this.singleResultCommand(db, command, params).flatMap(result => {
      val count: Long = result.getProperty(CountField)
      Option(count)
        .map(Success(_))
        .getOrElse(Failure(DatabaseCommandException(command, params, "'count' field was not present in result set")))
    })
  }

  /**
   * Executes a mutating command that returns a single element.
   */
  def singleResultCommand(db: ODatabaseDocument, command: String, params: Map[String, Any] = Map()): Try[OElement] = {
    this.command(db, command, params)
      .flatMap(assertOneDoc)
  }

  /**
   * Executes a mutating command that returns a single element.
   */
  def command(db: ODatabaseDocument, command: String, params: Map[String, Any] = Map()): Try[List[ODocument]] = {
    Try(db.command(command, params.asJava))
      .flatMap((rs: OResultSet) => TryWithResource(rs)(resultSetToDocList))
  }

  def executeMutation(db: ODatabaseDocument, script: String, params: Map[String, Any] = Map()): Try[Long] = {
    Try(db.execute("sql", script, params.asJava))
      .flatMap((rs: OResultSet) => TryWithResource(rs)(rs => getMutationCount(rs).get))
  }

  def execute(db: ODatabaseDocument, script: String, params: Map[String, Any] = Map()): Try[List[ODocument]] = {
    TryWithResource(db.execute("sql", script, params.asJava))(resultSetToDocList)
  }

  def getDocument(db: ODatabaseDocument, query: String, params: Map[String, Any] = Map()): Try[ODocument] = {
    Try(db.query(query, params.asJava))
      .flatMap((rs: OResultSet) => TryWithResource(rs)(resultSetToDocList))
      .flatMap(assertOneDoc)
  }

  def findDocument(db: ODatabaseDocument, query: String, params: Map[String, Any] = Map()): Try[Option[ODocument]] = {
    Try(db.query(query, params.asJava))
      .flatMap((rs: OResultSet) => TryWithResource(rs)(resultSetToDocList))
      .flatMap(assertZeroOrOneDoc)
  }

  def findDocumentAndMap[T](db: ODatabaseDocument, query: String, params: Map[String, Any] = Map())(m: ODocument => T): Try[Option[T]] = {
    this.findDocument(db, query, params).map(_.map(m(_)))
  }

  def mutateOneDocument(db: ODatabaseDocument, command: String, params: Map[String, Any] = Map()): Try[Unit] = {
    Try(db.command(command, params.asJava))
      .flatMap((rs: OResultSet) => TryWithResource(rs)(assertOneMutatedDoc(_).get))
  }

  def mutateOneDocumentWithScript(db: ODatabaseDocument, script: String, params: Map[String, Any] = Map()): Try[Unit] = {
    Try(db.execute("sql", script, params.asJava))
      .flatMap((rs: OResultSet) => TryWithResource(rs)(assertOneMutatedDoc(_).get))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Index Methods
  /////////////////////////////////////////////////////////////////////////////

  def indexContains(db: ODatabaseDocument, index: String, keys: List[_]): Try[Boolean] = {
    indexContains(db, index, new OCompositeKey(keys.asJava))
  }

  def indexContains(db: ODatabaseDocument, index: String, key: Any): Try[Boolean] = {
    val query = s"SELECT rid FROM INDEX:$index WHERE key = :key"
    val params = Map("key" -> key)
    findDocument(db, query, params).map(_.isDefined)
  }

  def getIdentityFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[_]): Try[ORID] = {
    getIdentityFromSingleValueIndex(db, index, new OCompositeKey(keys.asJava))
  }

  def getIdentityFromSingleValueIndex(db: ODatabaseDocument, index: String, key: Any): Try[ORID] = {
    val query = s"SELECT rid FROM INDEX:$index WHERE key = :key"
    val params = Map("key" -> key)
    getDocument(db, query, params).map(_.eval("rid").asInstanceOf[ORID])
  }

  def getIdentitiesFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[Any]): Try[List[ORID]] = {
    if (keys.isEmpty) {
      Success(List())
    } else {
      val processedKeys = keys.map(processIndexKey).asJava
      val params = Map("keys" -> processedKeys)
      val query = s"SELECT FROM INDEX:$index WHERE key IN :keys"
      OrientDBUtil.queryAndMap(db, query, params)(_.eval("rid").asInstanceOf[ORID])
    }
  }

  def findIdentityFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[_]): Try[Option[ORID]] = {
    findIdentityFromSingleValueIndex(db, index, new OCompositeKey(keys.asJava))
  }

  def findIdentityFromSingleValueIndex(db: ODatabaseDocument, index: String, key: Any): Try[Option[ORID]] = {
    val query = s"SELECT rid FROM INDEX:$index WHERE key = :key"
    val params = Map("key" -> key)
    findDocumentAndMap(db, query, params)(_.eval("rid").asInstanceOf[ORID])
  }

  def getDocumentFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[_]): Try[ODocument] = {
    this.getDocumentFromSingleValueIndex(db, index, new OCompositeKey(keys.asJava))
  }

  def getDocumentFromSingleValueIndex(db: ODatabaseDocument, index: String, key: Any): Try[ODocument] = {
    findDocumentFromSingleValueIndex(db, index, key)
      .flatMap {
        case Some(doc) => Success(doc)
        case None => Failure(EntityNotFoundException())
      }
  }

  def findDocumentFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[_]): Try[Option[ODocument]] = {
    findDocumentFromSingleValueIndex(db, index, new OCompositeKey(keys.asJava))
  }

  def findDocumentFromSingleValueIndex(db: ODatabaseDocument, index: String, key: Any): Try[Option[ODocument]] = {
    val query = s"SELECT rid FROM INDEX:$index WHERE key = :key"
    val params = Map("key" -> key)
    findDocumentAndMap(db, query, params)(_.getProperty("rid").asInstanceOf[ODocument
    ])
  }

  def getDocumentsFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[Any]): Try[List[ODocument]] = {
    if (keys.isEmpty) {
      Success(List())
    } else {
      val processedKeys = keys.map({
        // FIXME https://github.com/orientechnologies/orientdb/issues/8751
        case oc: OCompositeKey => new java.util.ArrayList(oc.getKeys)
        case l: List[_] => l.asJava
        case v: Any => v
      }).asJava
      val params = Map("keys" -> processedKeys)
      val query = s"SELECT rid FROM INDEX:$index WHERE key IN :keys"
      OrientDBUtil.query(db, query, params).map(_.map(_.getProperty("rid").asInstanceOf[OIdentifiable].getRecord.asInstanceOf[ODocument]))
    }
  }

  def deleteFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[_]): Try[Unit] = {
    deleteFromSingleValueIndex(db, index, new OCompositeKey(keys.asJava))
  }

  def deleteFromSingleValueIndex(db: ODatabaseDocument, index: String, key: Any): Try[Unit] = {
    val query = s"DELETE FROM INDEX:$index WHERE key = :key"
    val params = Map("key" -> key)
    mutateOneDocument(db, query, params)
  }

  def deleteFromSingleValueIndexIfExists(db: ODatabaseDocument, index: String, keys: List[_]): Try[Unit] = {
    deleteFromSingleValueIndexIfExists(db, index, new OCompositeKey(keys.asJava))
  }

  def deleteFromSingleValueIndexIfExists(db: ODatabaseDocument, index: String, key: Any): Try[Unit] = {
    val query = s"DELETE FROM INDEX:$index WHERE key = :key"
    val params = Map("key" -> key)
    command(db, query, params).map(_ => ())
  }


  /////////////////////////////////////////////////////////////////////////////
  // Sequence Methods
  /////////////////////////////////////////////////////////////////////////////

  def sequenceNext(db: ODatabaseDocument, sequence: String): Try[Long] = {
    Try {
      val seq = db.getMetadata.getSequenceLibrary.getSequence(sequence)
      val next = seq.next()
      next
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Misc Methods
  /////////////////////////////////////////////////////////////////////////////

  def resolveLink(identity: OIdentifiable): ODocument = {
    resolveOptionalLink(identity).getOrElse {
      throw new IllegalStateException("A link was null that was expected to have a value")
    }
  }

  def resolveOptionalLink(identity: OIdentifiable): Option[ODocument] = {
    Option(identity).map { t =>
      Option(t.getRecord[ODocument]) match {
        case Some(doc) =>
          doc
        case None =>
          throw new IllegalStateException("The OrientDB link points to a non-existent record: " + t.getIdentity)
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Helpers Methods
  /////////////////////////////////////////////////////////////////////////////

  def buildPagedQuery(baseQuery: String, limit: QueryLimit, offset: QueryOffset): String = {
    val limitOffsetString = (limit.value, offset.value) match {
      case (None, None) => ""
      case (Some(lim), None) => s" LIMIT $lim"
      case (None, Some(off)) => s" SKIP $off"
      case (Some(lim), Some(off)) => s" SKIP $off LIMIT $lim"
    }
    baseQuery + limitOffsetString
  }

  private[this] def assertOneDoc[T](list: List[T]): Try[T] = {
    list match {
      case first :: Nil =>
        Success(first)
      case _ :: _ =>
        Failure(MultipleValuesException())
      case Nil =>
        Failure(EntityNotFoundException())
    }
  }

  private[this] def assertZeroOrOneDoc[T](list: List[T]): Try[Option[T]] = {
    list match {
      case first :: Nil =>
        Success(Some(first))
      case _ :: _ =>
        Failure(MultipleValuesException())
      case Nil =>
        Success(None)
    }
  }

  private[this] def getMutationCount(rs: OResultSet): Try[Long] = Try {
    val count: Long = rs.next().getProperty(CountField)
    count
  }

  private[this] def assertOneMutatedDoc(rs: OResultSet): Try[Unit] = {
    getMutationCount(rs).flatMap {
      case 0 =>
        Failure(EntityNotFoundException())
      case 1 =>
        Success(())
      case _ =>
        Failure(MultipleValuesException())
    }
  }

  private[this] def resultSetToDocList(rs: OResultSet): List[ODocument] = {
    val docs = mutable.Buffer[ODocument]()
    while (rs.hasNext) {
      val doc = rs.next.toElement.asInstanceOf[ODocument]
      docs.append(doc)
    }
    rs.close()
    docs.toList
  }

  private[this] def processIndexKey(key: Any): Any = {
    key match {
      // Note we do this because of this
      //   https://github.com/orientechnologies/orientdb/issues/8751
      case oc: OCompositeKey => oc.getKeys
      case l: List[_] => l.asJava
      case v: Any => v
    }
  }
}
