import Dependencies.Compile._
import Dependencies.Test._
import _root_.com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import java.io.File

val commonSettings = Seq(
  organization := "com.convergencelabs",
  scalaVersion := "2.11.8",
  scalacOptions := Seq("-deprecation", "-feature"),
  fork := true,
  javaOptions += "-XX:MaxDirectMemorySize=16384m",
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
   // unmanagedSourceDirectories in Compile += baseDirectory.value / "target" / "scala-2.11" / "twirl" / "main",
    name := "convergence-server-core",
    libraryDependencies ++=
      akkaCore ++
      orientDb ++
      loggingAll ++
      Seq(
        scalapb,
        convergenceMessages,
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


lazy val dockerBuild = taskKey[Unit]("docker-build")
val serverNode = (project in file("server-node"))
  .configs(Configs.all: _*)
  .settings(commonSettings: _*)
  .settings(
    mainClass in Compile := Some("com.convergencelabs.server.ConvergenceServerNode")
  )
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "convergence-server-node",
    publishArtifact in (Compile, packageBin) := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false
  )
  .settings(
    dockerBuild := {
	  val dockerSrc = new File("server-node/src/docker")
	  val dockerTarget = new File("server-node/target/docker")
	  val packSrc = new File("server-node/target/pack")
	  val packTarget = new File("server-node/target/docker/pack")

	  IO.copyDirectory(dockerSrc, dockerTarget, true, false)
	  IO.copyDirectory(packSrc, packTarget, true, false)

	  "docker build -t nexus.convergencelabs.tech:18443/convergence-server-node:latest server-node/target/docker/" !
	}
  )
  .settings(dockerBuild <<= (dockerBuild dependsOn stage))
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
