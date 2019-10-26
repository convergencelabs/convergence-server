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
    mainClass in Compile := Some("com.convergencelabs.server.ConvergenceServerNode"),
    publishArtifact in (Compile, packageBin) := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false
  )
  .settings(
    sources in EditSource ++= (baseDirectory.value / "src/main/resources" * "reference.conf").get,
    variables in EditSource += "version" -> version.value,
    compile in Compile := {
      val compileAnalysis = (compile in Compile).value
      (edit in EditSource).value
      compileAnalysis
    },
    targetDirectory in EditSource := baseDirectory.value / "target/scala-2.12/classes/"
  )
  .enablePlugins(JavaAppPackaging, UniversalDeployPlugin)
