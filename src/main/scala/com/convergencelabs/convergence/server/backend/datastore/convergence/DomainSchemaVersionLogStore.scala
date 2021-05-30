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

import com.convergencelabs.convergence.server.backend.datastore.convergence.DomainSchemaDeltaLogStore.Params
import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.DomainSchemaVersionLogClass
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.schema.SchemaVersionUtil
import com.convergencelabs.convergence.server.model.DomainId
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import java.util.Date
import scala.util.Try

class DomainSchemaVersionLogStore(dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  private[this] val GetAllDomainVersions =
    "SELECT FROM DomainSchemaVersionLog ORDER BY domain ASC, date ASC"

  def getDomainSchemaVersions(): Try[Map[DomainId, String]] = withDb { db =>
    Try(db.getMetadata.getSchema.existsClass("DomainSchemaVersionLog")).flatMap {
      case true =>
        // TODO optimize this to only get the max version by date,
        //  or perhaps store the current version in the domain table,
        //  or create a new class that has domain by versions.
        OrientDBUtil.query(db, GetAllDomainVersions)
          .map { docs =>
            docs
              .map { doc =>
                val id = doc.eval("domain.id").asInstanceOf[String]
                val namespace = doc.eval("domain.namespace.id").asInstanceOf[String]
                val domainId = DomainId(namespace, id)

                val version = doc.eval("version").asInstanceOf[String]
                domainId -> version
              }
              .groupBy(t => t._1)
              .map { case (domainId, v) =>
                val versions = SchemaVersionUtil.sortVersionList(v.map(_._2))
                domainId -> versions.last
              }
          }
      case false =>
        OrientDBUtil.queryAndMap(db, "SELECT FROM Domain") { doc =>
          val domainId: String = doc.getProperty("id")
          val namespace: String = doc.eval("namespace.id").asInstanceOf[String]
          DomainId(namespace, domainId) -> SchemaVersionUtil.LegacyVersionNumber
        }.map(_.toMap)
    }
  }

  private[this] val DomainSchemaVersionLogQuery =
    "SELECT * FROM DomainSchemaVersionLog WHERE domain.namespace.id = :namespace AND domain.id = :id"

  def getDomainSchemaVersionLog(domainId: DomainId): Try[List[DomainSchemaVersionLogEntry]] = withDb { db =>
    val params = Map("namespace" -> domainId.namespace, "id" -> domainId.domainId)
    OrientDBUtil.queryAndMap(db, DomainSchemaVersionLogQuery, params) { doc =>
      val version = doc.getProperty("version").asInstanceOf[String]
      val date = doc.getProperty("date").asInstanceOf[Date].toInstant
      DomainSchemaVersionLogEntry(domainId, version, date)
    }
  }

  def createDomainSchemaVersionLogEntry(entry: DomainSchemaVersionLogEntry): Try[Unit] = withDb { db =>
    val DomainSchemaVersionLogEntry(domainId, version, date) = entry
    for {
      domainRid <- DomainStore.getDomainRid(domainId, db)
      _ <- Try {
        val doc: ODocument = db.newInstance(DomainSchemaVersionLogClass.ClassName)
        doc.setProperty(DomainSchemaVersionLogClass.Fields.Domain, domainRid)
        doc.setProperty(DomainSchemaVersionLogClass.Fields.Version, version)
        doc.setProperty(DomainSchemaVersionLogClass.Fields.Date, Date.from(date))
        db.save(doc)
        ()
      }
    } yield ()
  }

  private[this] val RemoveVersionLogForDomainCommand =
    s"""
       |DELETE FROM
       |  ${DomainSchemaVersionLogClass.ClassName}
       |WHERE
       |  domain.namespace.id = :namespace AND
       |  domain.id = :id""".stripMargin

  def removeVersionLogForDomain(domainId: DomainId): Try[Unit] = withDb { db =>
    val params = Map(Params.Namespace -> domainId.namespace, Params.Id -> domainId.domainId)
    OrientDBUtil.commandReturningCount(db, RemoveVersionLogForDomainCommand, params).map(_ => ())
  }
}



