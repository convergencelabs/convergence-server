import Dependencies.Compile._
import Dependencies.Test._

val commonSettings = Seq(
  organization := "com.convergencelabs",
  version := "1.0.0-M1-SNAPSHOT",
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-deprecation", "-feature"),
  fork := true
 )

val serverCore = (project in file("server-core")).
  settings(commonSettings: _*).
  settings(
    name := "convergence-server-core",
    libraryDependencies ++= 
      akkaCore ++ 
      orientDb ++ 
      loggingAll ++ 
      Seq(
        json4s, 
        commonsLang,
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
  settings(commonSettings: _*).
  settings(
    name := "convergence-server-testkit",
    libraryDependencies ++= 
    akkaCore ++ 
    orientDb ++ 
    loggingAll ++
    Seq(javaWebsockets)
  )
  .dependsOn(serverCore)

val tools = (project in file("server-tools")).
  settings(commonSettings: _*).
  settings(
    name := "convergence-server-tools",
    libraryDependencies ++= 
    orientDb ++ 
    loggingAll ++
    testingCore ++
    Seq(scallop, json4s)
  )

val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "convergence-server"
  ).
  aggregate(tools, serverCore, testkit)
  


















