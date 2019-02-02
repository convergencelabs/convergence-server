package com.convergencelabs.server.datastore.domain

import java.lang.{ Long => JavaLong }
import java.time.Instant
import java.util.Date

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.OrientDBUtil
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.domain.schema.DomainSchema
import scala.collection.JavaConverters._
import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.DomainUserType

case class DomainSession(
  id: String,
  userId: DomainUserId,
  connected: Instant,
  disconnected: Option[Instant],
  authMethod: String,
  client: String,
  clientVersion: String,
  clientMetaData: String,
  remoteHost: String)

object SessionStore {

  import schema.DomainSessionClass._

  def sessionToDoc(sl: DomainSession, db: ODatabaseDocument): Try[ODocument] = {
    DomainUserStore.getUserRid(sl.userId, db).recoverWith {
      case cause: Throwable =>
        Failure(new IllegalArgumentException(
          s"Could not create/update session because the user could not be found: ${sl.userId}"))
    }.map { userLink =>
      val doc: ODocument = db.newInstance(ClassName)
      doc.setProperty(Fields.Id, sl.id)
      doc.setProperty(Fields.User, userLink)
      doc.setProperty(Fields.Connected, Date.from(sl.connected))
      sl.disconnected.foreach(d => doc.setProperty(Fields.Disconnected, Date.from(d)))
      doc.setProperty(Fields.AuthMethod, sl.authMethod)
      doc.setProperty(Fields.Client, sl.client)
      doc.setProperty(Fields.ClientVersion, sl.clientVersion)
      doc.setProperty(Fields.ClientMetaData, sl.clientMetaData)
      doc.setProperty(Fields.RemoteHost, sl.remoteHost)
      doc
    }
  }

  def docToSession(doc: ODocument): DomainSession = {
    val username: String = doc.eval("user.username").asInstanceOf[String]
    val userType: String = doc.eval("user.userType").asInstanceOf[String]
    val connected: Date = doc.field(Fields.Connected, OType.DATE)
    val disconnected: Option[Date] = Option(doc.field(Fields.Disconnected, OType.DATE).asInstanceOf[Date])

    DomainSession(
      doc.field(Fields.Id),
      DomainUserId(DomainUserType.withName(userType), username),
      connected.toInstant(),
      disconnected map { _.toInstant() },
      doc.field(Fields.AuthMethod),
      doc.field(Fields.Client),
      doc.field(Fields.ClientVersion),
      doc.field(Fields.ClientMetaData),
      doc.field(Fields.RemoteHost))
  }

  def getDomainSessionRid(id: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Indices.Id, id)
  }

  object SessionQueryType extends Enumeration {
    val Normal, Anonymous, Admin, All, NonAdmin = Value
    def withNameOpt(s: String): Option[Value] = values.find(_.toString.toLowerCase() == s.toLowerCase())
  }
}

class SessionStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import SessionStore._
  import schema.DomainSessionClass._

  def nextSessionId: Try[String] = withDb { db =>
    OrientDBUtil.sequenceNext(db, DomainSchema.Sequences.SessionSeq) map (JavaLong.toString(_, 36))
  }

  def createSession(session: DomainSession): Try[Unit] = withDb { db =>
    sessionToDoc(session, db).flatMap { doc =>
      Try {
        db.save(doc)
        ()
      }
    }
  } recoverWith (handleDuplicateValue)

  def getSession(sessionId: String): Try[Option[DomainSession]] = withDb { db =>
    val query = "SELECT * FROM DomainSession WHERE id = :id"
    val params = Map("id" -> sessionId)
    OrientDBUtil.findDocumentAndMap(db, query, params)(SessionStore.docToSession(_))
  }

  def getAllSessions(limit: Option[Int], offset: Option[Int]): Try[List[DomainSession]] = withDb { db =>
    val baseQuery = s"SELECT * FROM DomainSession ORDER BY connected DESC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    OrientDBUtil.queryAndMap(db, query, Map())(SessionStore.docToSession(_))
  }

  private[this] val GetSessionsQuery = "SELECT * FROM DomainSession WHERE id IN :ids"
  def getSessions(sessionIds: Set[String]): Try[List[DomainSession]] = withDb { db =>
    val params = Map("ids" -> sessionIds.toList.asJava)
    OrientDBUtil.queryAndMap(db, GetSessionsQuery, params)(SessionStore.docToSession(_))
  }
  
  def getSessions(
    sessionId: Option[String],
    username: Option[String],
    userType: Option[DomainUserType.Value],
    remoteHost: Option[String],
    authMethod: Option[String],
    connectedOnly: Boolean,
    sessionType: SessionQueryType.Value,
    limit: Option[Int],
    offset: Option[Int]): Try[List[DomainSession]] = withDb { db =>
    var params = Map[String, Any]()
    var terms = List[String]()

    sessionId.foreach(sId => {
      params = params + ("id" -> s"%${sId}%")
      terms = "id LIKE :id" :: terms
    })

    userType.foreach(un => {
      params = params + ("userType" -> userType.toString.toLowerCase)
      terms = "user.userType = :userType" :: terms
    })
    
    username.foreach(un => {
      params = params + ("username" -> s"%${un}%")
      terms = "user.username LIKE :username" :: terms
    })

    remoteHost.foreach(rh => {
      params = params + ("remoteHost" -> s"%${rh}%")
      terms = "remoteHost LIKE :remoteHost" :: terms
    })

    authMethod.foreach(am => {
      params = params + ("authMethod" -> s"%${am}%")
      terms = "authMethod LIKE :authMethod" :: terms
    })

    if (connectedOnly) {
      terms = "disconnected IS NOT DEFINED" :: terms
    }

    this.getSessionTypeClause(sessionType).foreach(t => {
      terms = t :: terms
      ()
    })

    val where = terms match {
      case Nil =>
        ""
      case list =>
        "WHERE " + list.mkString(" AND ")
    }

    val baseQuery = s"SELECT * FROM DomainSession ${where} ORDER BY connected DESC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    OrientDBUtil.queryAndMap(db, query, params)(SessionStore.docToSession(_))
  }

  def getConnectedSessionsCount(sessionType: SessionQueryType.Value): Try[Long] = withDb { db =>
    val typeWhere = this.getSessionTypeClause(sessionType).map(t => s"AND ${t} ").getOrElse("")
    val query = s"SELECT count(*) as count FROM DomainSession WHERE disconnected IS NOT DEFINED ${typeWhere}"
    OrientDBUtil
      .getDocument(db, query, Map())
      .map(_.field("count").asInstanceOf[Long])
  }

  def setSessionDisconneted(sessionId: String, disconnectedTime: Instant): Try[Unit] = withDb { db =>
    val query = "UPDATE DomainSession SET disconnected = :disconnected WHERE id = :sessionId"
    val params = Map("disconnected" -> Date.from(disconnectedTime), "sessionId" -> sessionId)
    OrientDBUtil.mutateOneDocument(db, query, params)
  }

  private def getSessionTypeClause(sessionType: SessionQueryType.Value): Option[String] = {
    sessionType match {
      case SessionQueryType.All =>
        None
      case SessionQueryType.NonAdmin =>
        Some(s"not(user.userType = 'admin')")
      case SessionQueryType.Normal =>
        Some("user.userType = 'normal'")
      case SessionQueryType.Anonymous =>
        Some("user.userType = 'anonymous'")
      case SessionQueryType.Admin =>
        Some("user.userType = 'admin'")
    }
  }

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Indices.Id =>
          Failure(DuplicateValueException(Fields.Id))
        case _ =>
          Failure(e)
      }
  }
}
