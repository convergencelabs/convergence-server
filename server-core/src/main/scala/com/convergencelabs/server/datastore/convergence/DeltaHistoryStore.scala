package com.convergencelabs.server.datastore.convergence

import java.util.Date

import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument

import grizzled.slf4j.Logging

object DeltaHistoryStore {

  object Fields {
    val DeltaNo = "deltaNo"
    val Value = "value"

    val Domain = "domain"
    val Delta = "delta"
    val Status = "status"
    val Message = "message"
    val Date = "date"
  }

  // TODO: Determine what statuses we need
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
      deltaORID <- OrientDBUtil.getIdentityFromSingleValueIndex(db, Schema.ConvergenceDelta.Indices.DeltaNo, delta.deltaNo)
    } yield {
      val doc = db.newInstance(Schema.ConvergenceDeltaHistory.Class).asInstanceOf[ODocument]
      doc.setProperty(Fields.Delta, deltaORID)
      doc.setProperty(Fields.Status, status)
      message.foreach { doc.setProperty(Fields.Message, _) }
      doc.setProperty(Fields.Date, Date.from(date))
      doc.save()
      ()
    }
  }

  def getConvergenceDeltaHistory(deltaNo: Int): Try[Option[ConvergenceDeltaHistory]] = withDb { db =>
    OrientDBUtil
      .findIdentityFromSingleValueIndex(db, Schema.ConvergenceDelta.Indices.DeltaNo, deltaNo)
      .flatMap(_ match {
        case Some(deltaORID) =>
          OrientDBUtil.findDocumentFromSingleValueIndex(db, Schema.ConvergenceDeltaHistory.Indices.Delta, deltaORID)
        case None =>
          Success(None)
      })
      .map(_.map { doc =>
        val deltaDoc: ODocument = doc.getProperty(Fields.Delta)

        val deltaNo: Int = deltaDoc.getProperty(Fields.DeltaNo)
        val value: String = deltaDoc.getProperty(Fields.Value)

        val status: String = doc.getProperty(Fields.Status)
        val message: Option[String] = Option(doc.getProperty(Fields.Message))
        val date: Date = doc.getProperty(Fields.Date)

        val delta = ConvergenceDelta(deltaNo, value)
        ConvergenceDeltaHistory(delta, status, message, date.toInstant())
      })
  }

  def getConvergenceDBVersion(): Try[Int] = withDb { db =>
    OrientDBUtil
      .findDocument(
        db,
        "SELECT max(delta.deltaNo) as version FROM ConvergenceDeltaHistory WHERE status = :status",
        Map("status" -> Status.Success))
      .map(_.map(_.getProperty("version").asInstanceOf[Int]).getOrElse(0))
  }

  def isConvergenceDBHealthy(): Try[Boolean] = withDb { db =>
    OrientDBUtil
      .findDocument(
        db,
        "SELECT if(count(status) > 0, false, true) as healthy FROM ConvergenceDeltaHistory WHERE status = :status",
        Map("status" -> Status.Error))
      .map(_.map(_.field("healthy").asInstanceOf[Boolean]).getOrElse(true))
  }

  def saveDomainDeltaHistory(deltaHistory: DomainDeltaHistory): Try[Unit] = withDb { db =>
    val DomainDeltaHistory(domain, delta, status, message, date) = deltaHistory

    for {
      domainORID <- DomainStore.getDomainRid(domain, db)
      _ <- ensureDomainDeltaExists(db, delta)
      deltaORID <- OrientDBUtil
        .getIdentityFromSingleValueIndex(db, Schema.DomainDelta.Indices.DeltaNo, delta.deltaNo)
      _ <- OrientDBUtil
        .deleteFromSingleValueIndexIfExists(db, Schema.DomainDeltaHistory.Indices.DomainDelta, List(deltaORID, domainORID))
    } yield {
      val doc = db.newInstance(Schema.DomainDeltaHistory.Class).asInstanceOf[ODocument]
      doc.setProperty(Fields.Domain, domainORID)
      doc.setProperty(Fields.Delta, deltaORID)
      doc.setProperty(Fields.Status, status)
      message.foreach { doc.setProperty(Fields.Message, _) }
      doc.setProperty(Fields.Date, Date.from(date))
      doc.save()
      ()
    }
  }

  def removeDeltaHistoryForDomain(domainFqn: DomainFqn): Try[Unit] = withDb { db =>
    val query = "DELETE FROM DomainDeltaHistory WHERE domain IN (SELECT rid FROM INDEX:Domain.namespace_id WHERE KEY = [:namespace, :id])";
    val params = Map("namespace" -> domainFqn.namespace, "id" -> domainFqn.domainId)
    OrientDBUtil.command(db, query, params).map(_ => ())
  }

  def getDomainDeltaHistory(domainFqn: DomainFqn, deltaNo: Int): Try[Option[DomainDeltaHistory]] = withDb { db =>
    val DomainFqn(namespace, domainId) = domainFqn

    for {
      deltaORID <- OrientDBUtil.findIdentityFromSingleValueIndex(db, Schema.DomainDelta.Indices.DeltaNo, deltaNo)
      domainORID <- DomainStore.getDomainRid(domainFqn, db)
      doc <- {
        deltaORID match {
          case Some(deltaORID) =>
            OrientDBUtil.findDocumentFromSingleValueIndex(db, Schema.DomainDeltaHistory.Indices.DomainDelta, List(domainORID, deltaORID))
          case None =>
            Success(None)
        }
      }
    } yield {
      doc.map { doc =>
        val deltaDoc: ODocument = doc.field(Fields.Delta)

        val deltaNo: Int = deltaDoc.field(Fields.DeltaNo)
        val value: String = deltaDoc.field(Fields.Value)

        val status: String = doc.field(Fields.Status)
        val message: Option[String] = Option(doc.field(Fields.Message))
        val date: Date = doc.field(Fields.Date)

        val delta = DomainDelta(deltaNo, value)
        DomainDeltaHistory(domainFqn, delta, status, message, date.toInstant())
      }
    }
  }

  def getDomainDBVersion(domainFqn: DomainFqn): Try[Int] = withDb { db =>
    val DomainFqn(namespace, domainId) = domainFqn
    val query =
      s"""SELECT max(delta.deltaNo) as version
        |FROM ${Schema.DomainDeltaHistory.Class}
        |WHERE
        |  domain.namespace = :namespace AND
        |  domain.id = :id AND
        |  status = :status""".stripMargin
    val params = Map("id" -> domainId, "namespace" -> namespace, "status" -> Status.Success)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(_.field("version").asInstanceOf[Int]).getOrElse(0))
  }

  def isDomainDBHealthy(domainFqn: DomainFqn): Try[Boolean] = withDb { db =>
    val DomainFqn(namespace, domainId) = domainFqn
    val query =
      s"""SELECT if(count(status) > 0, false, true) as healthy
        |FROM ${Schema.DomainDeltaHistory.Class}
        |WHERE
        |  domain.namespace = :namespace AND
        |  domain.id = :id AND
        |  status = :status""".stripMargin
    val params = Map("id" -> domainId, "namespace" -> namespace, "status" -> Status.Error)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(_.field("healthy").asInstanceOf[Boolean]).getOrElse(true))
  }

  private[this] def ensureConvergenceDeltaExists(delta: ConvergenceDelta, db: ODatabaseDocument): Try[Unit] = {
    OrientDBUtil
      .indexContains(db, Schema.ConvergenceDelta.Indices.DeltaNo, delta.deltaNo)
      .flatMap { contains =>
        if (!contains) {
          Try {
            val ConvergenceDelta(deltaNo, value) = delta
            val doc = db.newInstance(Schema.ConvergenceDelta.Class).asInstanceOf[ODocument]
            doc.setProperty(Fields.DeltaNo, deltaNo)
            doc.setProperty(Fields.Value, value)
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
      .indexContains(db, Schema.DomainDelta.Indices.DeltaNo, delta.deltaNo)
      .flatMap { contains =>
        if (!contains)
          Try {
            val DomainDelta(deltaNo, value) = delta
            val doc = db.newInstance(Schema.DomainDelta.Class).asInstanceOf[ODocument]
            doc.setProperty(Fields.DeltaNo, deltaNo)
            doc.setProperty(Fields.Value, value)
            doc.save()
            ()
          }
        else {
          Success(())
        }
      }
  }
}
