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

import com.jsuereth.sbtpgp.PgpKeys._

//
// Global Settings
//

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

ThisBuild / organization := "com.convergencelabs"
ThisBuild / organizationName := "Convergence Labs, Inc."
ThisBuild / organizationHomepage := Some(url("https://convergencelabs.com"))

ThisBuild / homepage := Some(url("https://convergence.io"))

ThisBuild / licenses += "GPLv3" -> url("https://www.gnu.org/licenses/gpl-3.0.html")

ThisBuild / scmInfo := Some(ScmInfo(
  url("https://github.com/convergencelabs/convergence-server"),
  "https://github.com/convergencelabs/convergence-server.git"))

ThisBuild / scalaVersion := "2.13.7"

ThisBuild / developers := List(
  Developer(
    id = "mmacfadden",
    name = "Michael MacFadden",
    email = "michael@convergencelabs.com",
    url = url("https://convergencelabs.com")
  )
)

//
// ExhaustiveOtTest
//

lazy val ExhaustiveOtTest = Configuration.of("OT", "ot") extend Test

lazy val otTestConfig = Defaults.configSettings ++ Defaults.testTasks ++ Seq(
  ExhaustiveOtTest / scalaSource := baseDirectory.value / "src" / "test-ot" / "scala",
  ExhaustiveOtTest / parallelExecution := false
)

lazy val exhaustiveOtTestSettings =
  inConfig(ExhaustiveOtTest)(otTestConfig)

//
// Root Project
//

lazy val root = (project in file("."))
  .configs(ExhaustiveOtTest)
  .settings(
    exhaustiveOtTestSettings ++
      Seq(
        name := "Convergence Server",
        normalizedName := "convergence-server",
        description := "The Convergence Server core classes.",
        scalacOptions := Seq("-deprecation", "-feature"),
        Compile / discoveredMainClasses := Seq(),
        fork := true,
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
            testingAkka,

        //
        // SBT Build Info
        //
        buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
        buildInfoPackage := "com.convergencelabs.convergence.server"
      ))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(OrientDBPlugin)

//
// Universal Distribution Project
//

// Create some dummy tasks to help us publish the universal distribution.
val packageZip = taskKey[File]("package-zip")
val packageTgz = taskKey[File]("package-tgz")

lazy val dist = (project in file("distribution"))
  .enablePlugins(JavaAppPackaging, UniversalDeployPlugin)
  .settings(Seq(
    name := "Convergence Server Universal Distribution",
    normalizedName := "convergence-server-universal",
    description := "The universal binary distribution of the Convergence Server.",
    maintainer := "info@convergencelabs.com",

    crossPaths := false,
    Compile / discoveredMainClasses := Seq(),
    executableScriptName := "convergence-server",
    Compile / mainClass := Some("com.convergencelabs.convergence.server.ConvergenceServer"),

    bashScriptExtraDefines += """addApp "-c ${app_home}/../conf/convergence-server.conf"""",
    bashScriptExtraDefines += """addJava "-Dlog4j.configurationFile=${app_home}/../conf/log4j2.xml"""",
    bashScriptExtraDefines += """addJava "-Dnashorn.args=--no-deprecation-warning"""",

    batScriptExtraDefines += """call :add_app "-c %APP_HOME%\conf\convergence-server.conf"""",
    batScriptExtraDefines += """call :add_java "-Dlog4j.configurationFile=%APP_HOME%\conf\log4j2.xml"""",
    batScriptExtraDefines += """call :add_java "-Dnashorn.args=--no-deprecation-warning"""",

    packageZip := (Compile / baseDirectory).value / "target" / "universal" / (normalizedName.value + "-" + version.value + ".zip"),
    Universal / packageZip / artifact ~= { (art: Artifact) => art.withType("zip").withExtension("zip") },
    packageTgz := (Compile / baseDirectory).value / "target" / "universal" / (normalizedName.value + "-" + version.value + ".tgz"),
    Universal / packageTgz / artifact ~= { (art: Artifact) => art.withType("tgz").withExtension("tgz") },

    publish := (publish dependsOn(Universal / packageBin, Universal / packageZipTarball)).value,
    publishSigned := (publishSigned dependsOn(Universal / packageBin, Universal / packageZipTarball)).value,
  ))
  .settings(addArtifact(Universal / packageZip / artifact, Universal / packageZip))
  .settings(addArtifact(Universal / packageTgz / artifact, Universal / packageTgz))
  .enablePlugins(JavaAppPackaging, UniversalDeployPlugin)
  .dependsOn(root)
