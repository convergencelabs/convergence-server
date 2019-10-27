import Dependencies.Compile._
import Dependencies.Test._

lazy val root = (project in file("."))
  .settings(
    organization := "com.convergencelabs",
    name := "convergence-server",

    scalaVersion := "2.12.10",

    scalacOptions := Seq("-deprecation", "-feature"),
    fork := true,
    javaOptions += "-XX:MaxDirectMemorySize=16384m",
    resolvers += "Convergence Repo" at "https://nexus.dev.convergencelabs.tech/repository/maven-all/",
    publishTo := {
      val nexus = "https://nexus.dev.convergencelabs.tech/repository/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "maven-convergence-snapshots")
      else
        Some("releases" at nexus + "maven-convergence-releases")
    },
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
          parboiled,
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        ) ++
        Seq(orientDbServer % "test") ++
        testingCore ++
        testingAkka
  )
  .settings(
    mainClass in Compile := Some("com.convergencelabs.server.ConvergenceServer"),
    publishArtifact in (Compile, packageBin) := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false
  )
  .enablePlugins(JavaAppPackaging, UniversalDeployPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.convergencelabs.server"
  )
