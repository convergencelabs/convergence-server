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

// Generate a POM file with makePom, rather than an Ivy file.
ThisBuild / publishMavenStyle := true

// Do not include repositories in the POM.
ThisBuild / pomIncludeRepository := { _ => false }

// Publish to the Sonatype repo for snapshots and staging to maven central.
ThisBuild / publishTo := {
  val sonatypeOss = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at sonatypeOss + "content/repositories/snapshots")
  else Some("releases" at sonatypeOss + "service/local/staging/deploy/maven2")
}

// Pull the username and password from either a credentials file or
// environment variables.
(sys.env.get("SONATYPE_USER"), sys.env.get("SONATYPE_PASS")) match {
  case (Some(sonatypeUsername), Some(sonatypePassword)) =>
    ThisBuild / credentials += Credentials(
      "Sonatype Nexus Repository Manager",
      "oss.sonatype.org",
      sonatypeUsername,
      sonatypePassword)
  case _ =>
    ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials")
}
