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

import Dependencies.Compile._
import Dependencies.Test._

name := "convergence-server"
description := "Convergence Server"
homepage := Some(url("https://convergence.io"))

licenses += "GPLv3" -> url("https://www.gnu.org/licenses/gpl-3.0.html")

organization := "com.convergencelabs"
organizationName := "Convergence Labs, Inc."
organizationHomepage := Some(url("http://convergencelabs.com"))

maintainer := "info@convergencelabs.com"

scalaVersion := "2.12.10"

scalacOptions := Seq("-deprecation", "-feature")
fork := true
javaOptions += "-XX:MaxDirectMemorySize=16384m"

libraryDependencies ++=
  akkaCore ++
    orientDb ++
    loggingAll ++
    Seq(
      scalapb,
      convergenceProto,
      akkaHttp,
      json4s,
      jacksonYaml,
      json4sExt,
      akkaHttpJson4s,
      akkaHttpCors,
      commonsLang,
      jose4j,
      bouncyCastle,
      scrypt,
      netty,
      javaWebsockets,
      scallop,
      parboiled
    ) ++
    Seq(orientDbServer % "test") ++
    testingCore ++
    testingAkka

mainClass in Compile := Some("com.convergencelabs.convergence.server.ConvergenceServer")
discoveredMainClasses in Compile := Seq()

publishArtifact in (Compile, packageBin) := false
publishArtifact in (Compile, packageDoc) := false
publishArtifact in (Compile, packageSrc) := false

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "com.convergencelabs.convergence.server"

enablePlugins(JavaAppPackaging, UniversalDeployPlugin)
enablePlugins(BuildInfoPlugin)
enablePlugins(OrientDBPlugin)
