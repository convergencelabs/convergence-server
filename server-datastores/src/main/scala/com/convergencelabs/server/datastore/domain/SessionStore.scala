package com.convergencelabs.server.datastore

import java.time.Instant
import java.util.Date
import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Failure
import scala.util.Try

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import DomainStore.Fields._
import grizzled.slf4j.Logging

case class DomainSession(
  sessionId: String,
  username: String,
  active: Boolean,
  connected: Instant,
  disconnected: Option[Instant],
  authMethod: String,
  client: String,
  clientVersion: String,
  clientMetaData: Map[String, Any],
  remoteHost: String)

object SessionStore {
  val ClassName = "DomainSession"

  object Fields {
    val SessionId = "sessionId"
    val User = "user"
    val Active = "active"
    val Connected = "connected"
    val Disconnected = "disconnected"
    val AuthMethod = "authMethod"
    val Client = "client"
    val ClientVersion = "clientVersion"
    val ClientMetaData = "clientMetaData"
    val RemoteHost = "remoteHost"
  }

  def sessionToDoc(sl: DomainSession, db: ODatabaseDocumentTx): Try[ODocument] = {
    UserStore.getUserRid(sl.username, db).recoverWith {
      case cause: Exception =>
        Failure(new IllegalArgumentException(
          s"Could not create/update session because the user could not be found: ${sl.username}"))
    }.map { userLink =>
      val doc = db.newInstance(ClassName)
      doc.field(Fields.SessionId, sl.sessionId)
      doc.field(Fields.User, userLink)
      doc.field(Fields.Active, sl.active)
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
      doc.field(Fields.SessionId),
      username,
      doc.field(Fields.Active),
      connected.toInstant(),
      disconnected map { _.toInstant() },
      doc.field(Fields.AuthMethod),
      doc.field(Fields.Client),
      doc.field(Fields.ClientVersion),
      doc.field(Fields.ClientMetaData),
      doc.field(Fields.RemoteHost))
  }
}

class SessionStore(dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  def createSession(log: DomainSession): Try[CreateResult[Unit]] = tryWithDb { db =>
    SessionStore.sessionToDoc(log, db).map { doc =>
      db.save(doc)
      CreateSuccess(())
    }.get
  } recover {
    case e: ORecordDuplicatedException =>
      DuplicateValue
  }

  def getActiveSessions(): Try[List[DomainSession]] = tryWithDb { db =>
    val query =
      """SELECT *
        |FROM DomainSession
        |WHERE
        |  active = true""".stripMargin
    QueryUtil.query(query, Map(), db).map { SessionStore.docToSession(_) }
  }

  def sessionDisconnected(sessionId: String): Try[UpdateResult] = tryWithDb { db =>
    val query = "UPDATE DomainSession SET disconnected = :disconnected, active = false WHERE sessionId = :sessionId"
    val params = Map("disconnected" -> new Date(), "sessionId" -> sessionId)
    db.command(new OCommandSQL(query)).execute(params.asJava).asInstanceOf[Int] match {
      case 0 =>
        NotFound
      case _ =>
        UpdateSuccess
    }
  }
}
