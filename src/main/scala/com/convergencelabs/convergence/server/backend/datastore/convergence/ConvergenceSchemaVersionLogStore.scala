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

import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.ConvergenceSchemaVersionLogClass
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import scala.util.Try

class ConvergenceSchemaVersionLogStore(dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  def getConvergenceSchemaVersion(): Try[Option[String]] = withDb { db =>
    SchemaVersionUtil.getSchemaVersion(db, () => {
      OrientDBUtil.queryAndMap(db, ConvergenceSchemaVersionQuery) { doc =>
          doc.getProperty("version").asInstanceOf[String]
        }
    })
  }

  def createConvergenceSchemaVersionLogEntry(entry: ConvergenceSchemaVersionLogEntry): Try[Unit] = tryWithDb { db =>
    val ConvergenceSchemaVersionLogEntry(version, date) = entry
    val doc: ODocument = db.newInstance(ConvergenceSchemaVersionLogClass.ClassName)
    doc.setProperty(ConvergenceSchemaVersionLogClass.Fields.Version, version)
    doc.setProperty(ConvergenceSchemaVersionLogClass.Fields.Date, Date.from(date))
    db.save(doc)
    ()
  }

  private[this] val ConvergenceSchemaVersionQuery = "SELECT version FROM ConvergenceSchemaVersionLog"
}



