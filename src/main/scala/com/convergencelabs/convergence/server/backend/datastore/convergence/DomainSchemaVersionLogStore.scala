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

import com.convergencelabs.convergence.server.backend.datastore.convergence.DomainSchemaDeltaLogStore.Params
import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.DomainSchemaVersionLogClass
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.DomainId
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import scala.util.Try

class DomainSchemaVersionLogStore(dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  def getDomainSchemaVersion(domainId: DomainId): Try[Option[String]] = withDb { db =>
    SchemaVersionUtil.getSchemaVersion(db, () => {
      val params = Map("namespace" -> domainId.namespace, "id" -> domainId.domainId)
      OrientDBUtil.queryAndMap(db, DomainSchemaVersionQuery, params) { doc =>
          doc.getProperty("version").asInstanceOf[String]
        }
    })
  }

  private[this] val DomainSchemaVersionQuery =
    "SELECT version FROM DomainSchemaVersionLog WHERE domain.namespace.id = :namespace AND domain.id = :id"

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

  def removeVersionLogForDomain(domainId: DomainId): Try[Unit] = withDb { db =>
    val params = Map(Params.Namespace -> domainId.namespace, Params.Id -> domainId.domainId)
    OrientDBUtil.commandReturningCount(db, RemoveVersionLogForDomainCommand, params).map(_ => ())
  }

  private[this] val RemoveVersionLogForDomainCommand =
    s"DELETE FROM ${DomainSchemaVersionLogClass.ClassName} WHERE domain.namespace.id = :namespace AND domain.id = :id"

}



