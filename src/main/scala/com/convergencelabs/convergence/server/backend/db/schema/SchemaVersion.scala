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

package com.convergencelabs.convergence.server.backend.db.schema

/**
 * Represents the version of a database schema with a single dot-notation
 * version schema (e.g. 1.0, 1.2, 2.0, etc.). The version scheme is of
 * the form of (version).(patch). The "version" is the normal monotonically
 * increasing version of the schema as it is evolved on the happy path. The
 * patch version represents a backport of a fix to an earlier version.
 * The patch version will  be "0" when a new schema version is released.
 * The patch version will be incremented to 1, when the first patch is
 * applied, and incremented by one for any additional patch releases.
 *
 * @param version The main version number of the schema, starting at 1.
 * @param patch The patch version, starting at 0.
 */
case class SchemaVersion(version: Int, patch: Int) {
  val versionString = s"$version.$patch"

  def <(that: SchemaVersion): Boolean = {
    if (this.version < that.version) {
      true
    } else if (this.version > that.version) {
      false
    } else {
      this.patch < that.patch
    }
  }

  def >(that: SchemaVersion): Boolean = {
    if (this.version > that.version) {
      true
    } else if (this.version < that.version) {
      false
    } else {
      this.patch > that.patch
    }
  }
}

object SchemaVersion {
  def parse(version: String): Either[InvalidSchemaVersion, SchemaVersion] = {
    val MajorMinor = """(\d).(\d)""".r
    version match {
      case MajorMinor(major, minor) =>
        Right(SchemaVersion(major.toInt, minor.toInt))
      case _ =>
        Left(InvalidSchemaVersion(version))
    }
  }

  def parseUnsafe(version: String):  SchemaVersion = {
    SchemaVersion.parse(version).getOrElse {
      throw new IllegalArgumentException("Could not parse version: " + version)
    }
  }

  case class InvalidSchemaVersion(version: String)
}
