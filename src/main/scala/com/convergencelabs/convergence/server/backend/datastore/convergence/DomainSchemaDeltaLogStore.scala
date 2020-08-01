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

package com.convergencelabs.convergence.server.backend.datastore.convergence

import java.util.Date

import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.DomainSchemaDeltaLogClass
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.DomainId
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import scala.util.Try

class DomainSchemaDeltaLogStore(dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  import DomainSchemaDeltaLogStore._

  def createDomainDeltaLogEntries(entries: List[DomainSchemaDeltaLogEntry]): Try[Unit] = tryWithDb { db =>
    entries.foreach { entry =>
      val DomainSchemaDeltaLogEntry(domainId, seqNo, id, tag, script, status, message, appliedForVersion, date) = entry
      (for {
        domainRid <- DomainStore.getDomainRid(domainId, db)
        _ <- Try {
          val doc = db.newInstance(DomainSchemaDeltaLogClass.ClassName).asInstanceOf[ODocument]
          doc.setProperty(DomainSchemaDeltaLogClass.Fields.SeqNo, seqNo)
          doc.setProperty(DomainSchemaDeltaLogClass.Fields.Domain, domainRid)
          doc.setProperty(DomainSchemaDeltaLogClass.Fields.Id, id)
          tag.foreach(doc.setProperty(DomainSchemaDeltaLogClass.Fields.Tag, _))
          doc.setProperty(DomainSchemaDeltaLogClass.Fields.Version, appliedForVersion)
          doc.setProperty(DomainSchemaDeltaLogClass.Fields.Script, script)
          doc.setProperty(DomainSchemaDeltaLogClass.Fields.Status, status)
          message.foreach(doc.setProperty(DomainSchemaDeltaLogClass.Fields.Message, _))
          doc.setProperty(DomainSchemaDeltaLogClass.Fields.Date, Date.from(date))
          doc.save()
          ()
        }
      } yield ()).get
    }
  }

  def removeDeltaLogForDomain(domainId: DomainId): Try[Unit] = withDb { db =>
    val params = Map(Params.Namespace -> domainId.namespace, Params.Id -> domainId.domainId)
    OrientDBUtil.commandReturningCount(db, RemoveDeltaLogForDomainCommand, params).map(_ => ())
  }

  private[this] val RemoveDeltaLogForDomainCommand =
    s"DELETE FROM ${DomainSchemaDeltaLogClass.ClassName} WHERE domain.namespace.id = :namespace AND domain.id = :id"

  def appliedDeltasForDomain(domainId: DomainId): Try[List[DomainSchemaDeltaLogEntry]] = withDb { db =>
    val params = Map(Params.Namespace -> domainId.namespace, Params.Id -> domainId.domainId)
    OrientDBUtil.queryAndMap(db, GetDeltaLogForDomainQuery, params) { doc =>
      val seqNo: Int = doc.getProperty(DomainSchemaDeltaLogClass.Fields.SeqNo)
      val id: String = doc.getProperty(DomainSchemaDeltaLogClass.Fields.Id)
      val tag: Option[String] = Option(doc.getProperty(DomainSchemaDeltaLogClass.Fields.Id))
      val script: String = doc.getProperty(DomainSchemaDeltaLogClass.Fields.Script)
      val status: String = doc.field(DomainSchemaDeltaLogClass.Fields.Status)
      val message: Option[String] = Option(doc.field(DomainSchemaDeltaLogClass.Fields.Message))
      val appliedForVersion: String = doc.field(DomainSchemaDeltaLogClass.Fields.Version)
      val date: Date = doc.field(DomainSchemaDeltaLogClass.Fields.Date)
      DomainSchemaDeltaLogEntry(domainId, seqNo, id, tag, script, status, message, appliedForVersion, date.toInstant)
    }
  }
  private[this] val GetDeltaLogForDomainQuery =
    s"SELECT FROM ${DomainSchemaDeltaLogClass.ClassName} WHERE domain.namespace.id = :namespace AND domain.id = :id"


  def getMaxDeltaSequenceNumber(domainId: DomainId): Try[Int] = withDb { db =>
    val params = Map("id" -> domainId.domainId, "namespace" -> domainId.namespace)
    OrientDBUtil
      .getDocument(db, GetMaxDeltaSequenceNumberQuery, params)
      .map(doc => doc.getProperty("seqNo").asInstanceOf[Int])
  }

  private[this] val GetMaxDeltaSequenceNumberQuery =
    "SELECT max(seqNo) as seqNo FROM DomainSchemaDeltaLog WHERE domain.namespace.id = :namespace AND domain.id = :id"


  def isDomainDBHealthy(domainId: DomainId): Try[Boolean] = withDb { db =>
    val DomainId(namespace, id) = domainId
    val params = Map(Params.Id -> id, Params.Namespace -> namespace, Params.Status -> SchemaDeltaStatus.Error)
    OrientDBUtil
      .getDocument(db, IsDomainDBHealthyQuery, params)
      .map(_.field("healthy").asInstanceOf[Long] == 0)
  }

  private[this] val IsDomainDBHealthyQuery =
    s"""SELECT
       |  count(*) as count
       |FROM
       |  ${DomainSchemaDeltaLogClass.ClassName}
       |WHERE
       |  domain.namespace = :namespace AND
       |  domain.id = :id AND
       |  status = :status""".stripMargin

  def getLastDeltaErrorForDomain(domainId: DomainId): Try[Option[String]] = withDb { db =>
    val DomainId(namespace, id) = domainId
    val params = Map(Params.Id -> id, Params.Namespace -> namespace, Params.Status -> SchemaDeltaStatus.Error)
    OrientDBUtil
      .findDocument(db, GetLastDeltaErrorQuery, params)
      .map(_.map(_.getProperty("message").asInstanceOf[String]))
  }

  private[this] val GetLastDeltaErrorQuery =
    s"""SELECT
       |  message
       |FROM
       |  ${DomainSchemaDeltaLogClass.ClassName}
       |WHERE
       |  domain.namespace = :namespace AND
       |  domain.id = :id AND
       |  status = :status
       |  ORDER BY date DESC
       |  LIMIT 1""".stripMargin
}

object DomainSchemaDeltaLogStore {
  object Params {
    val Namespace = "namespace"
    val Id = "id"
    val Status = "status"
  }
}
