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

import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.{ConfigClass, ConvergenceSchemaVersionLogClass}
import com.convergencelabs.convergence.server.backend.db.schema.SchemaVersion
import com.orientechnologies.orient.core.db.document.ODatabaseDocument

import scala.util.{Success, Try}

object SchemaVersionUtil {
  def getSchemaVersion(db: ODatabaseDocument, versions: () => Try[List[String]]): Try[Option[String]] = {
    for {
      configExists <- Try(db.getMetadata.getSchema.existsClass(ConfigClass.ClassName))
      versionLogExists <- Try(db.getMetadata.getSchema.existsClass(ConvergenceSchemaVersionLogClass.ClassName))
      version <- (configExists, versionLogExists) match {
        case (false, _) =>
          // Nothing is installed.
          Success(None)
        case (true, false) =>
          // Identifies the pre 1.0 scheme before this version management
          // system was put in place.
          Success(Some("0.9"))
        case (true, true) =>
          versions()
            .flatMap(versions => {
              Try(versions.sortWith((a, b) => SchemaVersion.parseUnsafe(a) < SchemaVersion.parseUnsafe(b)))
            })
            .map(versions => Some(versions.last))
      }
    } yield version
  }
}
