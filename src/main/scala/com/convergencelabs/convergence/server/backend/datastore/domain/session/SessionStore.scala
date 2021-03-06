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

package com.convergencelabs.convergence.server.backend.datastore.domain.session

import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.datastore.domain.schema
import com.convergencelabs.convergence.server.backend.datastore.domain.schema.DomainSchema
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserStore
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, DuplicateValueException, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.domain.session
import com.convergencelabs.convergence.server.model.domain.session.DomainSession
import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import java.lang.{Long => JavaLong}
import java.time.Instant
import java.util.Date
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

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
  } recoverWith handleDuplicateValue

  def getSession(sessionId: String): Try[Option[DomainSession]] = withDb { db =>
    val query = "SELECT * FROM DomainSession WHERE id = :id"
    val params = Map("id" -> sessionId)
    OrientDBUtil.findDocumentAndMap(db, query, params)(SessionStore.docToSession)
  }

  def getAllSessions(offset: QueryOffset, limit: QueryLimit): Try[List[DomainSession]] = withDb { db =>
    val baseQuery = s"SELECT * FROM DomainSession ORDER BY connected DESC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    OrientDBUtil.queryAndMap(db, query, Map())(SessionStore.docToSession)
  }

  private[this] val GetSessionsQuery = "SELECT * FROM DomainSession WHERE id IN :ids"

  def getSessions(sessionIds: Set[String]): Try[List[DomainSession]] = withDb { db =>
    val params = Map("ids" -> sessionIds.toList.asJava)
    OrientDBUtil.queryAndMap(db, GetSessionsQuery, params)(SessionStore.docToSession)
  }

  def getSessions(sessionId: Option[String],
                  username: Option[String],
                  remoteHost: Option[String],
                  authMethod: Option[String],
                  excludeDisconnected: Boolean,
                  sessionType: SessionQueryType.Value,
                  offset: QueryOffset,
                  limit: QueryLimit
                 ): Try[PagedData[DomainSession]] = withDb { db =>
    var params = Map[String, Any]()
    var terms = List[String]()

    sessionId.foreach(sId => {
      params = params + ("id" -> s"%$sId%")
      terms = "id LIKE :id" :: terms
    })

    username.foreach(un => {
      params = params + ("username" -> s"%$un%")
      terms = "user.username LIKE :username" :: terms
    })

    remoteHost.foreach(rh => {
      params = params + ("remoteHost" -> s"%$rh%")
      terms = "remoteHost LIKE :remoteHost" :: terms
    })

    authMethod.foreach(am => {
      params = params + ("authMethod" -> s"%$am%")
      terms = "authMethod LIKE :authMethod" :: terms
    })

    if (excludeDisconnected) {
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

    val baseQuery = s"SELECT * FROM DomainSession $where ORDER BY connected DESC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)

    val countQuery = s"SELECT count(*) as count FROM DomainSession $where"

    for {
      count <- OrientDBUtil
        .getDocument(db, countQuery)
        .map(_.field("count").asInstanceOf[Long])
      sessions <- OrientDBUtil.queryAndMap(db, query, params)(SessionStore.docToSession)
    } yield {
      PagedData(sessions, offset.getOrZero, count)
    }

  }

  def getConnectedSessionsCount(sessionType: SessionQueryType.Value): Try[Long] = withDb { db =>
    val typeWhere = this.getSessionTypeClause(sessionType).map(t => s"AND $t ").getOrElse("")
    val query = s"SELECT count(*) as count FROM DomainSession WHERE disconnected IS NOT DEFINED $typeWhere"
    OrientDBUtil
      .getDocument(db, query, Map())
      .map(_.field("count").asInstanceOf[Long])
  }

  private val GetConnectedSessionQuery = s"SELECT id FROM DomainSession WHERE disconnected IS NOT DEFINED"

  def getConnectedSessions(): Try[List[String]] = withDb { db =>
    OrientDBUtil
      .queryAndMap(db, GetConnectedSessionQuery)(_.getProperty("id").asInstanceOf[String])
  }

  def setSessionDisconnected(sessionId: String, disconnectedTime: Instant): Try[Unit] =  {
    this.setSessionsDisconnected(Set(sessionId), disconnectedTime)
  }

  def setSessionsDisconnected(sessionIds: Set[String], disconnectedTime: Instant): Try[Unit] = withDb { db =>
    val query = "UPDATE DomainSession SET disconnected = :disconnected WHERE id IN :sessionIds"
    val params = Map("disconnected" -> Date.from(disconnectedTime), "sessionIds" -> sessionIds.asJava)
    OrientDBUtil.command(db, query, params).map(_ => ())
  }
  private[this] def getSessionTypeClause(sessionType: SessionQueryType.Value): Option[String] = {
    sessionType match {
      case SessionQueryType.All =>
        None
      case SessionQueryType.ExcludeConvergence =>
        Some(s"not(user.userType = 'convergence')")
      case SessionQueryType.Normal =>
        Some("user.userType = 'normal'")
      case SessionQueryType.Anonymous =>
        Some("user.userType = 'anonymous'")
      case SessionQueryType.Convergence =>
        Some("user.userType = 'convergence'")
    }
  }

  private[this] def handleDuplicateValue[T]: PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Indices.Id =>
          Failure(DuplicateValueException(Fields.Id))
        case _ =>
          Failure(e)
      }
  }
}


object SessionStore {

  import schema.DomainSessionClass._

  private def sessionToDoc(session: DomainSession, db: ODatabaseDocument): Try[ODocument] = {
    DomainUserStore.getUserRid(session.userId, db).recoverWith {
      case cause: Throwable =>
        Failure(new IllegalArgumentException(
          s"Could not create session because the user could not be found: ${session.userId}", cause))
    }.map { userLink =>
      val doc: ODocument = db.newInstance(ClassName)
      doc.setProperty(Fields.Id, session.id)
      doc.setProperty(Fields.User, userLink)
      doc.setProperty(Fields.Connected, Date.from(session.connected))
      session.disconnected.foreach(d => doc.setProperty(Fields.Disconnected, Date.from(d)))
      doc.setProperty(Fields.AuthMethod, session.authMethod)
      doc.setProperty(Fields.Client, session.client)
      doc.setProperty(Fields.ClientVersion, session.clientVersion)
      doc.setProperty(Fields.ClientMetaData, session.clientMetaData)
      doc.setProperty(Fields.RemoteHost, session.remoteHost)
      doc
    }
  }

  private def docToSession(doc: ODocument): DomainSession = {
    val username: String = doc.eval("user.username").asInstanceOf[String]
    val userType: String = doc.eval("user.userType").asInstanceOf[String]
    val connected: Date = doc.field(Fields.Connected, OType.DATE)
    val disconnected: Option[Date] = Option(doc.field(Fields.Disconnected, OType.DATE).asInstanceOf[Date])

    session.DomainSession(
      doc.field(Fields.Id),
      DomainUserId(DomainUserType.withName(userType), username),
      connected.toInstant,
      disconnected map {
        _.toInstant()
      },
      doc.field(Fields.AuthMethod),
      doc.field(Fields.Client),
      doc.field(Fields.ClientVersion),
      doc.field(Fields.ClientMetaData),
      doc.field(Fields.RemoteHost))
  }

  private[domain ]def getDomainSessionRid(id: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Indices.Id, id)
  }
}