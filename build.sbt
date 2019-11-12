/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
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

mainClass in Compile := Some("com.convergencelabs.server.ConvergenceServer")
publishArtifact in (Compile, packageBin) := false
publishArtifact in (Compile, packageDoc) := false
publishArtifact in (Compile, packageSrc) := false

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "com.convergencelabs.server"

enablePlugins(JavaAppPackaging, UniversalDeployPlugin)
enablePlugins(BuildInfoPlugin)
enablePlugins(OrientDBPlugin)
