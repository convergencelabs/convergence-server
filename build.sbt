import Dependencies.Compile._
import Dependencies.Test._


val commonSettings = packSettings ++ Seq(
  organization := "com.convergencelabs",
  scalaVersion := "2.11.8",
  scalacOptions := Seq("-deprecation", "-feature"),
  fork := true,
  packMain := Map("test-server" -> "com.convergencelabs.server.testkit.TestServer"),
  packResourceDir += (baseDirectory.value / "server-testkit" / "test-server" -> "test-server")
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
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      ) ++
      testingCore ++
      testingAkka
  )

val testkit = (project in file("server-testkit")).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(Testing.settings: _*).
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

lazy val dockerSettings = Seq(
  dockerfile in docker := {
  
    new Dockerfile {
      from("centos:7")
      run("yum", "--assumeyes", "install", "java-1.8.0-openjdk-devel")
      add(new java.io.File("target/pack"), "/opt/convergence")
      env("JAVA_HOME", "/usr/lib/jvm/java-1.8.0")
      env("PATH", "$JAVA_HOME/bin:$PATH")
      workDir("/opt/convergence/")
      expose(8080)
      entryPoint("/opt/convergence/bin/test-server")
    }
  },
  imageNames in docker := {
    Seq(ImageName("convergence-server"))
  }
)

val root = (project in file(".")).
  enablePlugins(DockerPlugin).
  configs(Configs.all: _*).
  settings(commonSettings: _*).
  settings(Testing.settings: _*).
  settings(dockerSettings:_*).
  settings(
    name := "convergence-server",
    aggregate in pack := false,
    aggregate in docker := false
  ).
  aggregate(tools, serverCore, testkit, e2eTests)
  
  docker <<= docker dependsOn pack
