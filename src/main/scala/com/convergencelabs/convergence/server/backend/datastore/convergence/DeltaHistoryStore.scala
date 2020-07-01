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

import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.{ConvergenceDeltaClass, ConvergenceDeltaHistoryClass, DomainDeltaClass, DomainDeltaHistoryClass}
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.DomainId
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import scala.util.{Success, Try}

object DeltaHistoryStore {

  object Params {
    val Namespace = "namespace"
    val Id = "id"
    val Status = "status"
  }

  object Status {
    val Error = "error"
    val Success = "success"
  }
}

class DeltaHistoryStore(dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {
  import DeltaHistoryStore._

  def saveConvergenceDeltaHistory(deltaHistory: ConvergenceDeltaHistory): Try[Unit] = withDb { db =>
    val ConvergenceDeltaHistory(delta, status, message, date) = deltaHistory

    for {
      _ <- ensureConvergenceDeltaExists(delta, db)
      deltaORID <- OrientDBUtil.getIdentityFromSingleValueIndex(db, ConvergenceDeltaClass.Indices.DeltaNo, delta.deltaNo)
    } yield {
      val doc = db.newInstance(ConvergenceDeltaHistoryClass.ClassName).asInstanceOf[ODocument]
      doc.setProperty(ConvergenceDeltaHistoryClass.Fields.Delta, deltaORID)
      doc.setProperty(ConvergenceDeltaHistoryClass.Fields.Status, status)
      message.foreach { doc.setProperty(ConvergenceDeltaHistoryClass.Fields.Message, _) }
      doc.setProperty(ConvergenceDeltaHistoryClass.Fields.Date, Date.from(date))
      doc.save()
      ()
    }
  }

  def getConvergenceDeltaHistory(deltaNo: Int): Try[Option[ConvergenceDeltaHistory]] = withDb { db =>
    OrientDBUtil
      .findIdentityFromSingleValueIndex(db, ConvergenceDeltaClass.Indices.DeltaNo, deltaNo)
      .flatMap {
        case Some(deltaORID) =>
          OrientDBUtil.findDocumentFromSingleValueIndex(db, ConvergenceDeltaHistoryClass.Indices.Delta, deltaORID)
        case None =>
          Success(None)
      }
      .map(_.map { doc =>
        val deltaDoc: ODocument = doc.getProperty(ConvergenceDeltaHistoryClass.Fields.Delta)

        val deltaNo: Int = deltaDoc.getProperty(ConvergenceDeltaClass.Fields.DeltaNo)
        val script: String = deltaDoc.getProperty(ConvergenceDeltaClass.Fields.Script)

        val status: String = doc.getProperty(ConvergenceDeltaHistoryClass.Fields.Status)
        val message: Option[String] = Option(doc.getProperty(ConvergenceDeltaHistoryClass.Fields.Message))
        val date: Date = doc.getProperty(ConvergenceDeltaHistoryClass.Fields.Date)

        val delta = ConvergenceDelta(deltaNo, script)
        ConvergenceDeltaHistory(delta, status, message, date.toInstant)
      })
  }

  def getConvergenceDBVersion(): Try[Int] = withDb { db =>
    OrientDBUtil
      .findDocument(
        db,
        "SELECT max(delta.deltaNo) as version FROM ConvergenceDeltaHistory WHERE status = :status",
        Map(Params.Status -> Status.Success))
      .map(_.map(_.getProperty("version").asInstanceOf[Int]).getOrElse(0))
  }

  def isConvergenceDBHealthy(): Try[Boolean] = withDb { db =>
    OrientDBUtil
      .findDocument(
        db,
        "SELECT if(count(*) > 0, false, true) as healthy FROM ConvergenceDeltaHistory WHERE status = :status",
        Map(Params.Status -> Status.Error))
      .map(_.forall(_.field("healthy").asInstanceOf[Boolean]))
  }

  def saveDomainDeltaHistory(deltaHistory: DomainDeltaHistory): Try[Unit] = withDb { db =>
    val DomainDeltaHistory(domain, delta, status, message, date) = deltaHistory

    for {
      domainORID <- DomainStore.getDomainRid(domain, db)
      _ <- ensureDomainDeltaExists(db, delta)
      deltaORID <- OrientDBUtil
        .getIdentityFromSingleValueIndex(db, DomainDeltaClass.Indices.DeltaNo, delta.deltaNo)
      _ <- OrientDBUtil
        .deleteFromSingleValueIndexIfExists(db, DomainDeltaHistoryClass.Indices.DomainDelta, List(deltaORID, domainORID))
    } yield {
      val doc = db.newInstance(DomainDeltaHistoryClass.ClassName).asInstanceOf[ODocument]
      doc.setProperty(DomainDeltaHistoryClass.Fields.Domain, domainORID)
      doc.setProperty(DomainDeltaHistoryClass.Fields.Delta, deltaORID)
      doc.setProperty(DomainDeltaHistoryClass.Fields.Status, status)
      message.foreach { doc.setProperty(DomainDeltaHistoryClass.Fields.Message, _) }
      doc.setProperty(DomainDeltaHistoryClass.Fields.Date, Date.from(date))
      doc.save()
      ()
    }
  }

  def removeDeltaHistoryForDomain(domainId: DomainId): Try[Unit] = withDb { db =>
    val query = "DELETE FROM DomainDeltaHistory WHERE domain IN (SELECT FROM Domain WHERE namespace.id = :namespace AND id =:id)"
    val params = Map(Params.Namespace -> domainId.namespace, Params.Id -> domainId.domainId)
    OrientDBUtil.commandReturningCount(db, query, params).map(_ => ())
  }

  def getDomainDeltaHistory(domainId: DomainId, deltaNo: Int): Try[Option[DomainDeltaHistory]] = withDb { db =>
    for {
      deltaORID <- OrientDBUtil.findIdentityFromSingleValueIndex(db, DomainDeltaClass.Indices.DeltaNo, deltaNo)
      domainORID <- DomainStore.getDomainRid(domainId, db)
      doc <- {
        deltaORID match {
          case Some(deltaORID) =>
            OrientDBUtil.findDocumentFromSingleValueIndex(db, DomainDeltaHistoryClass.Indices.DomainDelta, List(domainORID, deltaORID))
          case None =>
            Success(None)
        }
      }
    } yield {
      doc.map { doc =>
        val deltaDoc: ODocument = doc.getProperty(DomainDeltaHistoryClass.Fields.Delta)

        val deltaNo: Int = deltaDoc.getProperty(DomainDeltaClass.Fields.DeltaNo)
        val script: String = deltaDoc.getProperty(DomainDeltaClass.Fields.Script)

        val status: String = doc.field(DomainDeltaHistoryClass.Fields.Status)
        val message: Option[String] = Option(doc.field(DomainDeltaHistoryClass.Fields.Message))
        val date: Date = doc.field(DomainDeltaHistoryClass.Fields.Date)

        val delta = DomainDelta(deltaNo, script)
        DomainDeltaHistory(domainId, delta, status, message, date.toInstant)
      }
    }
  }

  def getDomainDBVersion(domainId: DomainId): Try[Int] = withDb { db =>
    val DomainId(namespace, id) = domainId
    val query =
      s"""SELECT max(delta.deltaNo) as version
        |FROM ${DomainDeltaHistoryClass.ClassName}
        |WHERE
        |  domain.namespace = :namespace AND
        |  domain.id = :id AND
        |  status = :status""".stripMargin
    val params = Map("id" -> id, "namespace" -> namespace, "status" -> Status.Success)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(_.field("version").asInstanceOf[Int]).getOrElse(0))
  }

  def isDomainDBHealthy(domainId: DomainId): Try[Boolean] = withDb { db =>
    val DomainId(namespace, id) = domainId
    val query =
      s"""SELECT if(count(*) > 0, false, true) as healthy
        |FROM ${DomainDeltaHistoryClass.ClassName}
        |WHERE
        |  domain.namespace = :namespace AND
        |  domain.id = :id AND
        |  status = :status""".stripMargin
    val params = Map("id" -> id, "namespace" -> namespace, "status" -> Status.Error)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.forall(_.field("healthy").asInstanceOf[Boolean]))
  }

  private[this] def ensureConvergenceDeltaExists(delta: ConvergenceDelta, db: ODatabaseDocument): Try[Unit] = {
    OrientDBUtil
      .indexContains(db, ConvergenceDeltaClass.Indices.DeltaNo, delta.deltaNo)
      .flatMap { contains =>
        if (!contains) {
          Try {
            val ConvergenceDelta(deltaNo, value) = delta
            val doc = db.newInstance(ConvergenceDeltaClass.ClassName).asInstanceOf[ODocument]
            doc.setProperty(ConvergenceDeltaClass.Fields.DeltaNo, deltaNo)
            doc.setProperty(ConvergenceDeltaClass.Fields.Script, value)
            doc.save()
            ()
          }
        } else {
          Success(())
        }
      }
  }

  private[this] def ensureDomainDeltaExists(db: ODatabaseDocument, delta: DomainDelta): Try[Unit] = {
    OrientDBUtil
      .indexContains(db, DomainDeltaClass.Indices.DeltaNo, delta.deltaNo)
      .flatMap { contains =>
        if (!contains)
          Try {
            val DomainDelta(deltaNo, value) = delta
            val doc = db.newInstance(DomainDeltaClass.ClassName).asInstanceOf[ODocument]
            doc.setProperty(DomainDeltaClass.Fields.DeltaNo, deltaNo)
            doc.setProperty(DomainDeltaClass.Fields.Script, value)
            doc.save()
            ()
          }
        else {
          Success(())
        }
      }
  }
}
