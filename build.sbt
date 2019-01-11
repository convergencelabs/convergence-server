import Dependencies.Compile._
import Dependencies.Test._
import _root_.com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import java.io.File

val commonSettings = Seq(
  organization := "com.convergencelabs",
  scalaVersion := "2.12.6",
  scalacOptions := Seq("-deprecation", "-feature"),
  fork := true,
  javaOptions += "-XX:MaxDirectMemorySize=16384m",
  resolvers += "Convergence Repo" at "https://nexus.dev.int.convergencelabs.tech/repository/maven-all/",
  publishTo := {
    val nexus = "https://nexus.dev.int.convergencelabs.tech/repository/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "maven-convergence-snapshots")
    else
      Some("releases"  at nexus + "maven-convergence-releases")
  }
 )

 val serverOt = (project in file("server-ot")).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(Testing.settings: _*).
  settings(
    name := "convergence-server-ot",
    libraryDependencies ++=
      orientDb ++
      loggingAll ++
      Seq(
        json4s,
        commonsLang,
        jose4j,
        bouncyCastle,
        scrypt,
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      ) ++
      testingCore
  )

val serverCore = (project in file("server-core")).
  enablePlugins(SbtTwirl).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(Testing.settings: _*).
  settings(
    name := "convergence-server-core",
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
        commonsEmail,
        jose4j,
        bouncyCastle,
        scrypt,
        netty,
        javaWebsockets,
        scallop,
        parboiled,
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      ) ++
      Seq(orientDbServer % "test") ++
      testingCore ++
      testingAkka
  ).dependsOn(serverOt)

val serverNode = (project in file("server-node"))
  .configs(Configs.all: _*)
  .settings(commonSettings: _*)
  .settings(
    mainClass in Compile := Some("com.convergencelabs.server.ConvergenceServerNode")
  )
  .enablePlugins(JavaAppPackaging, UniversalDeployPlugin)
  .settings(
    name := "convergence-server-node",
    publishArtifact in (Compile, packageBin) := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false
  )
  .dependsOn(serverCore)

val testkit = (project in file("server-testkit")).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(Testing.settings: _*).
  settings(
    name := "convergence-server-testkit",
    libraryDependencies ++=
    akkaCore ++
    orientDb ++
    Seq(orientDbServer, orientDbStudio) ++
    loggingAll ++
    testingCore ++
    Seq(javaWebsockets)
  )
  .dependsOn(serverCore)

val e2eTests = (project in file("server-e2e-tests")).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(Testing.settings: _*).
  settings(
    name := "convergence-server-e2e-tests",
    //unmanagedSourceDirectories in Compile += baseDirectory.value / "src/e2e/scala",
    libraryDependencies ++=
      loggingAll ++
      testingCore
  ).
  dependsOn(testkit)

val root = (project in file(".")).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(Testing.settings: _*).
  settings(
    name := "convergence-server",
    publishArtifact in (Compile, packageBin) := false, // there are no binaries
    publishArtifact in (Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in (Compile, packageSrc) := false
  ).
  aggregate(serverOt, serverCore, serverNode, testkit, e2eTests)
