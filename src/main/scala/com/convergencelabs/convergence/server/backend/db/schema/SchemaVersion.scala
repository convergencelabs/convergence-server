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
case class SchemaVersion(major: Int, minor: Int) {
  val versionString = s"$major.$minor"

  def <(that: SchemaVersion): Boolean = {
    if (this.major < that.major) {
      true
    } else if (this.major > that.major) {
      false
    } else {
      this.minor < that.minor
    }
  }

  def >(that: SchemaVersion): Boolean = {
    if (this.major > that.major) {
      true
    } else if (this.major < that.major) {
      false
    } else {
      this.minor > that.minor
    }
  }
}
