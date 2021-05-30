package com.convergencelabs.convergence.server.backend.db.schema

import com.convergencelabs.convergence.server.backend.datastore.convergence.schema.{ConfigClass, ConvergenceSchemaVersionLogClass}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument

import scala.util.{Success, Try}

object SchemaVersionUtil {
  val LegacyVersionNumber: String = "0.9"

  /**
   * This method gets the version of a currently installed database.
   *
   * @param db       The database that contains version information.
   * @param versions A helper method that abstracts how versions are
   *                 queried for in the database. This allows generalization
   *                 to look for the Convergence version or the Domain versions.
   * @return None if nothing is installed, or Some with a string representation
   *         of the current schema version.
   */
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
          Success(Some(LegacyVersionNumber))
        case (true, true) =>
          versions()
            .flatMap(versions => Try(sortVersionList(versions)))
            .map {
              case Nil =>
                None
              case list =>
                Some(list.last)
            }
      }
    } yield version
  }

  def sortVersionList(versions: List[String]): List[String] = {
    versions.sortWith((a, b) => SchemaVersion.parseUnsafe(a) < SchemaVersion.parseUnsafe(b))
  }

  def computeSchemaVersionStatus(codeVersion: SchemaVersion, domainVersionString: String): DatabaseSchemaVersionStatus = {
    SchemaVersion
      .parse(domainVersionString)
      .fold({ _ =>
        DatabaseSchemaVersionError()
      }, { domainVersion =>
        if (domainVersion < codeVersion) {
          DatabaseSchemaNeedsUpgrade()
        } else if (domainVersion > codeVersion) {
          DatabaseSchemaVersionToHigh()
        } else {
          DatabaseSchemaVersionOk()
        }
      })
  }
}
