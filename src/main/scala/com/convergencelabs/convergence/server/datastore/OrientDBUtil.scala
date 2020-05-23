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

package com.convergencelabs.convergence.server.datastore

import com.convergencelabs.convergence.server.util.TryWithResource
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.index.{OCompositeKey, OIndex}
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
    this.singleResultCommand(db, command, params).flatMap( result => {
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
    Try(db.execute("sql", script, params.asJava)).flatMap(getMutationCount)
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
    for {
      oIndex <- getIndex(db, index)
      contains <- Try(oIndex.contains(key))
    } yield {
      contains
    }
  }

  def getIdentityFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[_]): Try[ORID] = {
    getIdentityFromSingleValueIndex(db, index, new OCompositeKey(keys.asJava))
  }

  def getIdentityFromSingleValueIndex(db: ODatabaseDocument, index: String, key: Any): Try[ORID] = {
    for {
      oIndex <- getIndex(db, index)
      identity <- Try(Option(oIndex.get(key).asInstanceOf[OIdentifiable]))
        .flatMap {
          case Some(doc) => Success(doc.getIdentity)
          case None => Failure(EntityNotFoundException())
        }
    } yield {
      identity
    }
  }

  def getIdentitiesFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[Any]): Try[List[ORID]] = {
    if (keys.isEmpty) {
      Success(List())
    } else {
      val processedKeys = keys.map({
        // FIXME https://github.com/orientechnologies/orientdb/issues/8751
        case oc: OCompositeKey => oc.getKeys
        case l: List[_] => l.asJava
        case v: Any => v
      }).asJava

      val params = Map("keys" -> processedKeys)
      val query = s"SELECT FROM INDEX:$index WHERE key IN :keys"
      OrientDBUtil.queryAndMap(db, query, params) { doc =>
        val rid = doc.eval("rid").asInstanceOf[ORID]
        rid
      }

      //      val oIndex = db.getMetadata.getIndexManager.getIndex(index)
      //      Try(oIndex.iterateEntries(processedKeys.asJava, false)).map { cursor =>
      //        cursor.toEntries().asScala.toList.map { entry =>
      //          entry.getValue.getIdentity
      //        }
      //      }
    }
  }

  def findIdentityFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[_]): Try[Option[ORID]] = {
    findIdentityFromSingleValueIndex(db, index, new OCompositeKey(keys.asJava))
  }

  def findIdentityFromSingleValueIndex(db: ODatabaseDocument, index: String, key: Any): Try[Option[ORID]] = {
    Try(Option(db.getMetadata.getIndexManager.getIndex(index).get(key).asInstanceOf[OIdentifiable]).map(_.getIdentity))
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
    Try(Option(db
      .getMetadata
      .getIndexManager
      .getIndex(index)
      .get(key).asInstanceOf[OIdentifiable]))
      .map(_.map(_.getRecord.asInstanceOf[ODocument]))
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
      val query = s"SELECT FROM INDEX:$index WHERE key IN :keys"
      OrientDBUtil.query(db, query, params).map(_.map(_.getProperty("rid").asInstanceOf[OIdentifiable].getRecord.asInstanceOf[ODocument]))
    }
  }

  def deleteFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[_]): Try[Unit] = {
    deleteFromSingleValueIndex(db, index, new OCompositeKey(keys.asJava))
  }

  def deleteFromSingleValueIndex(db: ODatabaseDocument, index: String, key: Any): Try[Unit] = {
    // FIXME this should work, but for some reason does not seem to work.
    //    for {
    //      oIndex <- getIndex(db, index)
    //      deleted <- Try(oIndex.remove(key))
    //    } yield {
    //      deleted match {
    //        case true =>
    //          Success(())
    //        case false =>
    //          Failure(EntityNotFoundException())
    //      }
    //    }

    for {
      oIndex <- getIndex(db, index)
      rid <- Try(Option(oIndex.get(key).asInstanceOf[ORID]))
      _ <- rid match {
        case Some(rid) =>
          Try(db.delete(rid)).map(_ => ())
        case None =>
          Failure(EntityNotFoundException())
      }
    } yield {
      ()
    }
  }

  def deleteFromSingleValueIndexIfExists(db: ODatabaseDocument, index: String, keys: List[_]): Try[Unit] = {
    deleteFromSingleValueIndexIfExists(db, index, new OCompositeKey(keys.asJava))
  }

  def deleteFromSingleValueIndexIfExists(db: ODatabaseDocument, index: String, key: Any): Try[Unit] = {
    Try {
      Option(db.getMetadata.getIndexManager.getIndex(index).get(key).asInstanceOf[OIdentifiable]).map(_.getIdentity)
    }.flatMap {
      case Some(rid) => Try(db.delete(rid)).map(_ => ())
      case None => Success(())
    }
  }

  def getIndex(db: ODatabaseDocument, index: String): Try[OIndex[_]] = {
    Option(db.getMetadata.getIndexManager.getIndex(index)) match {
      case Some(oIndex) =>
        Success(oIndex)
      case None =>
        // TODO Workaround for https://github.com/orientechnologies/orientdb/issues/8397
        db.getMetadata.reload()
        Option(db.getMetadata.getIndexManager.getIndex(index)) match {
          case Some(oIndex) =>
            Success(oIndex)
          case None =>
            Failure(new IllegalArgumentException("Index not found: " + index))
        }

    }
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

  def buildPagedQuery(baseQuery: String, limit: Option[Int], offset: Option[Int]): String = {
    val limitOffsetString = (limit, offset) match {
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
    rs.close()
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
}
