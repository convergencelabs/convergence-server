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

import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.ConvergenceSchemaDeltaLogClass
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import scala.util.{Success, Try}

class ConvergenceSchemaDeltaLogStore(dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  import DomainSchemaDeltaLogStore._

  def createConvergenceDeltaEntries(entries: List[ConvergenceSchemaDeltaLogEntry]): Try[Unit] = tryWithDb { db =>
    entries.foreach { entry =>
      val ConvergenceSchemaDeltaLogEntry(sequenceNumber, id, tag, script, status, message, appliedForVersion, date) = entry
      val doc = db.newInstance(ConvergenceSchemaDeltaLogClass.ClassName).asInstanceOf[ODocument]
      doc.setProperty(ConvergenceSchemaDeltaLogClass.Fields.SeqNo, sequenceNumber)
      doc.setProperty(ConvergenceSchemaDeltaLogClass.Fields.Id, id)
      tag.foreach(t => doc.setProperty(ConvergenceSchemaDeltaLogClass.Fields.tag, t))
      doc.setProperty(ConvergenceSchemaDeltaLogClass.Fields.Version, appliedForVersion)
      doc.setProperty(ConvergenceSchemaDeltaLogClass.Fields.Script, script)
      doc.setProperty(ConvergenceSchemaDeltaLogClass.Fields.Status, status)
      message.foreach(doc.setProperty(ConvergenceSchemaDeltaLogClass.Fields.Message, _))
      doc.setProperty(ConvergenceSchemaDeltaLogClass.Fields.Date, Date.from(date))
      doc.save()
      ()
    }

  }

  def getMaxDeltaSequenceNumber(): Try[Int] = withDb { db =>
    getOrDefaultIfSchemaManagementNotFound(db,
      () => OrientDBUtil
        .getDocument(db, GetMaxDeltaSequenceNumberQuery)
        .map(doc => doc.getProperty("seqNo").asInstanceOf[Int]),
      () => 0)
  }

  val GetMaxDeltaSequenceNumberQuery = "SELECT max(seqNo) as seqNo FROM ConvergenceSchemaDeltaLog"

  def appliedConvergenceDeltas(): Try[List[ConvergenceSchemaDeltaLogEntry]] = withDb { db =>
    getOrDefaultIfSchemaManagementNotFound(db,
      () => OrientDBUtil.queryAndMap(db, GetConvergenceDeltas, Map()) { doc =>
        val seqNo: Int = doc.getProperty(ConvergenceSchemaDeltaLogClass.Fields.SeqNo)
        val id: String = doc.getProperty(ConvergenceSchemaDeltaLogClass.Fields.Id)
        val tag: Option[String] = Option(doc.getProperty(ConvergenceSchemaDeltaLogClass.Fields.tag))
        val script: String = doc.getProperty(ConvergenceSchemaDeltaLogClass.Fields.Script)
        val status: String = doc.getProperty(ConvergenceSchemaDeltaLogClass.Fields.Status)
        val message: Option[String] = Option(doc.getProperty(ConvergenceSchemaDeltaLogClass.Fields.Message))
        val appliedForVersion: String = doc.getProperty(ConvergenceSchemaDeltaLogClass.Fields.Version)
        val date: Date = doc.getProperty(ConvergenceSchemaDeltaLogClass.Fields.Date)
        ConvergenceSchemaDeltaLogEntry(seqNo, id, tag, script, status, message, appliedForVersion, date.toInstant)
      },
      () => List()
    )
  }

  private[this] val GetConvergenceDeltas = "SELECT * FROM ConvergenceSchemaDeltaLog"

  def isConvergenceDBHealthy(): Try[Boolean] = withDb { db =>
    OrientDBUtil
      .getDocument(db, ConvergenceHealthQuery, Map(Params.Status -> SchemaDeltaStatus.Error))
      .map(_.field("count").asInstanceOf[Long] == 0)
  }

  private[this] val ConvergenceHealthQuery = "SELECT count(*) as count FROM ConvergenceSchemaDeltaLog WHERE status = :status"

  def getLastDeltaError(): Try[Option[String]] = withDb { db =>
    OrientDBUtil
      .findDocument(db, ConvergenceErrorQuery, Map(Params.Status -> SchemaDeltaStatus.Error))
      .map(_.map(_.getProperty("message").asInstanceOf[String]))
  }

  private[this] val ConvergenceErrorQuery = "SELECT  message FROM ConvergenceSchemaDeltaLog WHERE status = :status ORDER BY date DESC LIMIT 1"


  private[this] def getOrDefaultIfSchemaManagementNotFound[T](db: ODatabaseDocument,
                                                              computed: () => Try[T],
                                                              default: () => T): Try[T] = {
    if (db.getMetadata.getSchema.existsClass(ConvergenceSchemaDeltaLogClass.ClassName)) {
      computed()
    } else {
      Success(default())
    }
  }
}



