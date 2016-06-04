import Dependencies.Compile._
import Dependencies.Test._


val commonSettings = Seq(
  organization := "com.convergencelabs",
  scalaVersion := "2.11.8",
  scalacOptions := Seq("-deprecation", "-feature"),
  fork := true,
  publishTo := {
    val nexus = "https://builds.convergencelabs.tech/nexus/repository/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "maven-snapshots/") 
    else
      Some("releases"  at nexus + "maven-releases")
  },
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
 )

val serverCore = (project in file("server-core")).
  enablePlugins(SbtTwirl).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(Testing.settings: _*).
  settings(
    packSettings ++ 
    Seq(
      packMain := Map("server-node" -> "com.convergencelabs.server.ConvergenceServerNode"),
      packResourceDir += (baseDirectory.value / "src" / "config" -> "config")
    )
    ++ publishPackArchiveTgz
  ).
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
      testingCore ++
      testingAkka
  )
  
val serverNode = (project in file("server-node")).
  enablePlugins(DockerPlugin).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(
    packSettings ++ 
    Seq(
      packMain := Map("server-node" -> "com.convergencelabs.server.ConvergenceServerNode"),
      packResourceDir += (baseDirectory.value / "src" / "config" -> "config")
    )
    ++ publishPackArchiveTgz
  ).
  settings(Seq(
    docker <<= (docker dependsOn pack),
    dockerfile in docker := {
      new Dockerfile {
        from("java:openjdk-8-jre")
        add(new java.io.File("server-node/target/pack"), "/opt/convergence")
        add("https://github.com/kelseyhightower/confd/releases/download/v0.11.0/confd-0.11.0-linux-amd64", "/usr/local/bin/confd")
        add(new java.io.File("server-node/src/confd"), "/etc/confd/")
        add(new java.io.File("server-node/src/bin"), "/opt/convergence/bin")
        run("chmod", "+x", "/usr/local/bin/confd")
        expose(8080)
        workDir("/opt/convergence/")
        entryPoint("/opt/convergence/bin/boot")
      }
    },
    imageNames in docker := {
      Seq(ImageName("convergence-server-node"))
    }
  )).
  settings(
    name := "convergence-server-node",
    publishArtifact in (Compile, packageBin) := false, 
    publishArtifact in (Compile, packageDoc) := false, 
    publishArtifact in (Compile, packageSrc) := false
  ).
  dependsOn(serverCore)

val testkit = (project in file("server-testkit")).
  enablePlugins(DockerPlugin).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(Testing.settings: _*).
  settings(Seq(
    docker <<= (docker dependsOn pack),
    dockerfile in docker := {
      new Dockerfile {
        from("java:openjdk-8-jre")
        add(new java.io.File("server-testkit/target/pack"), "/opt/convergence")
        expose(8080)
        workDir("/opt/convergence/")
        entryPoint("/opt/convergence/bin/test-server")
      }
    },
    imageNames in docker := {
      Seq(ImageName("convergence-test-server"))
    }
  )).
  settings(packSettings ++ Seq(
    packMain := Map("test-server" -> "com.convergencelabs.server.testkit.TestServer"),
    packResourceDir += (baseDirectory.value / "test-server" -> "test-server")
  )).
  settings(
    name := "convergence-server-testkit",
    libraryDependencies ++= 
    akkaCore ++ 
    orientDb ++ 
    loggingAll ++
    testingCore ++
    Seq(javaWebsockets)
  )
  .dependsOn(serverCore)
  
docker in testkit <<= docker dependsOn (pack in testkit)
  
  
val tools = (project in file("server-tools")).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(Testing.settings: _*).
  settings(
    name := "convergence-server-tools",
    libraryDependencies ++= 
    orientDb ++ 
    loggingAll ++
    testingCore ++
    Seq(scallop, json4s)
  )
  
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
  aggregate(tools, serverCore, serverNode, testkit, e2eTests)
  

