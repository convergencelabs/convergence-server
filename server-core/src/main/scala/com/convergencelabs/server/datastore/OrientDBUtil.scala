package com.convergencelabs.server.datastore

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable.Buffer
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.util.TryWithResource
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.executor.OResultSet
import com.orientechnologies.orient.core.metadata.security.OIdentity
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.index.OCompositeKey

object OrientDBUtil {

  val CountField = "count"

  def query(db: ODatabaseDocument, query: String, params: Map[_, _] = Map()): Try[List[ODocument]] = {
    TryWithResource(db.query(query, params.asJava))(resultSetToDocList(_))
  }

  /**
   * Executes a mutating command within the database and returns the number of
   * records that were modified. This methods assumes there is a single result
   * set produced by the command, and that the result set contains a 'count'
   * field indicating the number of mutated records.
   */
  def command(db: ODatabaseDocument, query: String, params: Map[_, _] = Map()): Try[Int] = {
    Try(db.command(query, params.asJava)).flatMap { rs =>
      if (rs.hasNext()) {
        val element = rs.next.toElement
        if (rs.hasNext()) {
          Failure(new DatabaseCommandException(query, params, "The result set unexpectedly contained more than one result"))
        } else {
          rs.close()
          val count: Int = element.getProperty(CountField)
          Option(count)
            .map(Success(_))
            .getOrElse(Failure(new DatabaseCommandException(query, params, "'count' field was not present in result set")))
        }
      } else {
        Failure(new DatabaseCommandException(query, params, "No ResultSet was returned from the command"))
      }
    }
  }
  
  def execute(db: ODatabaseDocument, query: String, params: Map[_, _] = Map()): Try[List[ODocument]] = {
    TryWithResource(db.execute("sql", query, params.asJava))(resultSetToDocList(_))
  }

  def getDocument(db: ODatabaseDocument, query: String, params: Map[String, Any] = Map()): Try[ODocument] = {
    val rs = db.query(query, params.asJava)
    TryWithResource(rs)(resultSetToDocList(_)) flatMap (assertOneDoc(_))
  }

  def findDocument(db: ODatabaseDocument, query: String, params: Map[String, Any] = Map()): Try[Option[ODocument]] = {
    val rs = db.query(query, params.asJava)
    TryWithResource(rs)(resultSetToDocList(_)) flatMap (assertZeroOrOneDoc(_))
  }

  def mutateOneDocument(db: ODatabaseDocument, command: String, params: Map[String, Any] = Map()): Try[Unit] = {
    val rs: OResultSet = db.command(command, params.asJava)
    TryWithResource(rs)(assertOneMutatedDoc(_))
  }

  def updateDocumentWithScript(db: ODatabaseDocument, script: String, params: Map[String, Any] = Map()): Try[Unit] = {
    val rs: OResultSet = db.execute("sql", script, params.asJava)
    TryWithResource(rs)(assertOneMutatedDoc(_))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Index Methods
  ////////////////////////////////////////////////////////////////////////////
  def indexContains(db: ODatabaseDocument, index: String, keys: List[_]): Try[Boolean] = {
    indexContains(db, index, new OCompositeKey(keys.asJava))
  }
  
  def indexContains(db: ODatabaseDocument, index: String, key: Any): Try[Boolean] = {
    Try(db.getMetadata.getIndexManager.getIndex(index).contains(key))
  }

  def getIdentityFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[_]): Try[ORID] = {
    getIdentityFromSingleValueIndex(db, index, new OCompositeKey(keys.asJava))
  }

  def getIdentityFromSingleValueIndex(db: ODatabaseDocument, index: String, key: Any): Try[ORID] = {
    Try(Option(db.getMetadata.getIndexManager.getIndex(index).get(key).asInstanceOf[OIdentifiable]))
      .flatMap(_ match {
        case Some(doc) => Success(doc.getIdentity)
        case None => Failure(EntityNotFoundException())
      })
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
    .flatMap(_ match {
        case Some(doc) => Success(doc)
        case None => Failure(EntityNotFoundException())
      })
  }

  def findDocumentFromSingleValueIndex(db: ODatabaseDocument, index: String, keys: List[_]): Try[Option[ODocument]] = {
    findDocumentFromSingleValueIndex(db, index, new OCompositeKey(keys.asJava))
  }

  def findDocumentFromSingleValueIndex(db: ODatabaseDocument, index: String, key: Any): Try[Option[ODocument]] = {
    Try(Option(db.getMetadata.getIndexManager.getIndex(index).get(key).asInstanceOf[ODocument]))
  }

  def deleteFromSingleValueIndexIfExists(db: ODatabaseDocument, index: String, keys: List[_]): Try[Unit] = {
    deleteFromSingleValueIndexIfExists(db, index, new OCompositeKey(keys.asJava))
  }

  def deleteFromSingleValueIndexIfExists(db: ODatabaseDocument, index: String, key: Any): Try[Unit] = {
    Try(Option(db.getMetadata.getIndexManager.getIndex(index).get(key).asInstanceOf[ODocument]))
      .flatMap(_ match {
        case Some(rid) => Try(db.delete(rid)).map(_ => ())
        case None => Success(())
      })
  }

  /////////////////////////////////////////////////////////////////////////////
  // Helpers Methods
  ////////////////////////////////////////////////////////////////////////////

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
      case first :: rest =>
        Failure(MultipleValuesException())
      case Nil =>
        Failure(EntityNotFoundException())
    }
  }

  private[this] def assertZeroOrOneDoc[T](list: List[T]): Try[Option[T]] = {
    list match {
      case first :: Nil =>
        Success(Some(first))
      case first :: rest =>
        Failure(MultipleValuesException())
      case Nil =>
        Success(None)
    }
  }

  private[this] def assertOneMutatedDoc(rs: OResultSet) = {
    val list = resultSetToDocList(rs)
    list.size match {
      case 0 =>
        Failure(EntityNotFoundException())
      case 1 =>
        Success(())
      case _ =>
        Failure(MultipleValuesException())
    }
  }

  private[this] def resultSetToDocList(rs: OResultSet): List[ODocument] = {
    val docs = Buffer[ODocument]()
    while (rs.hasNext()) {
      val doc = rs.next.toElement.asInstanceOf[ODocument]
      docs.append(doc)
    }
    docs.toList
  }
}
