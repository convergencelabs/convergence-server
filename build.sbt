import Dependencies.Compile._
import Dependencies.Test._
import java.io.File

val commonSettings = Seq(
  organization := "com.convergencelabs",
  scalaVersion := "2.11.8",
  scalacOptions := Seq("-deprecation", "-feature"),
  fork := true,
  publishTo := {
    val nexus = "https://nexus.convergencelabs.tech/repository/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "maven-snapshots/") 
    else
      Some("releases"  at nexus + "maven-releases")
  },
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  resolvers += "Maven Public" at "https://nexus.convergencelabs.tech/repository/maven-public"
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
 
 val serverDatastore = (project in file("server-datastores")).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(Testing.settings: _*).
  settings(
    name := "convergence-server-datastores",
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
  ).dependsOn(serverOt)
 
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
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      ) ++
      Seq(orientDbServer % "test") ++
      testingCore ++
      testingAkka
  ).dependsOn(serverDatastore)
  
  
lazy val dockerBuild = taskKey[Unit]("docker-build")
val serverNode = (project in file("server-node"))
  .configs(Configs.all: _*)
  .settings(commonSettings: _*)
  .settings(
    packSettings ++ 
    Seq(
	  packJvmOpts := Map("server-node" -> Seq("-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager")),
      packMain := Map("server-node" -> "com.convergencelabs.server.ConvergenceServerNode"),
      packResourceDir += (baseDirectory.value / "src" / "config" -> "config")
    )
  )
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
  .settings(dockerBuild <<= (dockerBuild dependsOn pack))  
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
    Seq(orientDbServer) ++
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
  aggregate(serverOt, serverDatastore, serverCore, serverNode, testkit, e2eTests)
  