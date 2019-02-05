package com.convergencelabs.server.datastore.convergence

import java.util.Date

import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.convergence.schema.ConvergenceDeltaClass
import com.convergencelabs.server.datastore.convergence.schema.ConvergenceDeltaHistoryClass
import com.convergencelabs.server.datastore.convergence.schema.DomainDeltaClass
import com.convergencelabs.server.datastore.convergence.schema.DomainDeltaHistoryClass
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument

import grizzled.slf4j.Logging

object DeltaHistoryStore {

  object Params {
    val DeltaNo = "deltaNo"
    val Value = "value"

    val Domain = "domain"
    val Delta = "delta"
    val Status = "status"
    val Message = "message"
    val Date = "date"
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
      .flatMap(_ match {
        case Some(deltaORID) =>
          OrientDBUtil.findDocumentFromSingleValueIndex(db, ConvergenceDeltaHistoryClass.Indices.Delta, deltaORID)
        case None =>
          Success(None)
      })
      .map(_.map { doc =>
        val deltaDoc: ODocument = doc.getProperty(ConvergenceDeltaHistoryClass.Fields.Delta)

        val deltaNo: Int = deltaDoc.getProperty(ConvergenceDeltaClass.Fields.DeltaNo)
        val script: String = deltaDoc.getProperty(ConvergenceDeltaClass.Fields.Script)

        val status: String = doc.getProperty(ConvergenceDeltaHistoryClass.Fields.Status)
        val message: Option[String] = Option(doc.getProperty(ConvergenceDeltaHistoryClass.Fields.Message))
        val date: Date = doc.getProperty(ConvergenceDeltaHistoryClass.Fields.Date)

        val delta = ConvergenceDelta(deltaNo, script)
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
        "SELECT if(count(*) > 0, false, true) as healthy FROM ConvergenceDeltaHistory WHERE status = :status",
        Map("status" -> Status.Error))
      .map(_.map(_.field("healthy").asInstanceOf[Boolean]).getOrElse(true))
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

  def removeDeltaHistoryForDomain(domainFqn: DomainFqn): Try[Unit] = withDb { db =>
    val query = "DELETE FROM DomainDeltaHistory WHERE domain IN (SELECT rid FROM INDEX:Domain.namespace_id WHERE KEY = [:namespace, :id])";
    val params = Map("namespace" -> domainFqn.namespace, "id" -> domainFqn.domainId)
    OrientDBUtil.command(db, query, params).map(_ => ())
  }

  def getDomainDeltaHistory(domainFqn: DomainFqn, deltaNo: Int): Try[Option[DomainDeltaHistory]] = withDb { db =>
    val DomainFqn(namespace, domainId) = domainFqn

    for {
      deltaORID <- OrientDBUtil.findIdentityFromSingleValueIndex(db, DomainDeltaClass.Indices.DeltaNo, deltaNo)
      domainORID <- DomainStore.getDomainRid(domainFqn, db)
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
        DomainDeltaHistory(domainFqn, delta, status, message, date.toInstant())
      }
    }
  }

  def getDomainDBVersion(domainFqn: DomainFqn): Try[Int] = withDb { db =>
    val DomainFqn(namespace, domainId) = domainFqn
    val query =
      s"""SELECT max(delta.deltaNo) as version
        |FROM ${DomainDeltaHistoryClass.ClassName}
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
      s"""SELECT if(count(*) > 0, false, true) as healthy
        |FROM ${DomainDeltaHistoryClass.ClassName}
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
