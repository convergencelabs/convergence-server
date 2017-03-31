package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.Date

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueExcpetion
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.QueryUtil
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging

case class DomainSession(
  id: String,
  username: String,
  connected: Instant,
  disconnected: Option[Instant],
  authMethod: String,
  client: String,
  clientVersion: String,
  clientMetaData: String,
  remoteHost: String)

object SessionStore {
  val ClassName = "DomainSession"

  val SessionIdIndex = "DomainSession.id"

  object Fields {
    val Id = "id"
    val User = "user"
    val Connected = "connected"
    val Disconnected = "disconnected"
    val AuthMethod = "authMethod"
    val Client = "client"
    val ClientVersion = "clientVersion"
    val ClientMetaData = "clientMetaData"
    val RemoteHost = "remoteHost"
  }

  def sessionToDoc(sl: DomainSession, db: ODatabaseDocumentTx): Try[ODocument] = {
    DomainUserStore.getUserRid(sl.username, db).recoverWith {
      case cause: Exception =>
        Failure(new IllegalArgumentException(
          s"Could not create/update session because the user could not be found: ${sl.username}"))
    }.map { userLink =>
      val doc = db.newInstance(ClassName)
      doc.field(Fields.Id, sl.id)
      doc.field(Fields.User, userLink)
      doc.field(Fields.Connected, Date.from(sl.connected))
      sl.disconnected foreach { date =>
        doc.field(Fields.Disconnected, Date.from(date))
      }
      doc.field(Fields.AuthMethod, sl.authMethod)
      doc.field(Fields.Client, sl.client)
      doc.field(Fields.ClientVersion, sl.clientVersion)
      doc.field(Fields.ClientMetaData, sl.clientMetaData)
      doc.field(Fields.RemoteHost, sl.remoteHost)
      doc
    }
  }

  def docToSession(doc: ODocument): DomainSession = {
    val username: String = doc.field("user.username")
    val connected: Date = doc.field(Fields.Connected, OType.DATE)
    val disconnected: Option[Date] = Option(doc.field(Fields.Disconnected, OType.DATE).asInstanceOf[Date])

    DomainSession(
      doc.field(Fields.Id),
      username,
      connected.toInstant(),
      disconnected map { _.toInstant() },
      doc.field(Fields.AuthMethod),
      doc.field(Fields.Client),
      doc.field(Fields.ClientVersion),
      doc.field(Fields.ClientMetaData),
      doc.field(Fields.RemoteHost))
  }

  def getDomainSessionRid(id: String, db: ODatabaseDocumentTx): Try[ORID] = {
    val query = "SELECT @RID as rid FROM DomainSession WHERE id = :id"
    val params = Map("id" -> id)
    QueryUtil.lookupMandatoryDocument(query, params, db) map { _.eval("rid").asInstanceOf[ORID] }
  }
}

class SessionStore(dbProvider: DatabaseProvider)
    extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  def createSession(session: DomainSession): Try[Unit] = tryWithDb { db =>
    SessionStore.sessionToDoc(session, db).map { doc =>
      db.save(doc)
      ()
    }.get
  } recoverWith {
    case e: ORecordDuplicatedException => handleDuplicateValue(e)
  }

  def getSession(sessionId: String): Try[Option[DomainSession]] = tryWithDb { db =>
    val query = "SELECT * FROM DomainSession WHERE id = :id"
    val params = Map("id" -> sessionId)
    QueryUtil.lookupOptionalDocument(query, params, db).map { SessionStore.docToSession(_) }
  }

  def getSessions(limit: Option[Int], offset: Option[Int]): Try[List[DomainSession]] = tryWithDb { db =>
    val baseQuery = "SELECT * FROM DomainSession ORDER BY connected DESC"
    val query = QueryUtil.buildPagedQuery(baseQuery, limit, offset)
    QueryUtil.query(query, Map(), db).map { SessionStore.docToSession(_) }
  }

  def getConnectedSessions(limit: Option[Int], offset: Option[Int]): Try[List[DomainSession]] = tryWithDb { db =>
    val baseQuery = "SELECT * FROM DomainSession WHERE disconnected IS NOT DEFINED ORDER BY connected DESC"
    val query = QueryUtil.buildPagedQuery(baseQuery, limit, offset) 
    QueryUtil.query(query, Map(), db).map { SessionStore.docToSession(_) }
  }

  def getConnectedSessionsCount(): Try[Long] = tryWithDb { db =>
    val query = "SELECT count(id) as count FROM DomainSession WHERE disconnected IS NOT DEFINED"
    QueryUtil.lookupMandatoryDocument(query, Map(), db).map { _.field("count").asInstanceOf[Long] }.get
  }

  def setSessionDisconneted(sessionId: String, disconnectedTime: Instant): Try[Unit] = tryWithDb { db =>
    val query = "UPDATE DomainSession SET disconnected = :disconnected WHERE id = :sessionId"
    val params = Map("disconnected" -> Date.from(disconnectedTime), "sessionId" -> sessionId)
    db.command(new OCommandSQL(query)).execute(params.asJava).asInstanceOf[Int] match {
      case 0 =>
        throw EntityNotFoundException()
      case _ =>
        ()
    }
  }

  private[this] def handleDuplicateValue[T](e: ORecordDuplicatedException): Try[T] = {
    e.getIndexName match {
      case SessionStore.SessionIdIndex =>
        Failure(DuplicateValueExcpetion(SessionStore.Fields.Id))
      case _ =>
        Failure(e)
    }
  }
}
