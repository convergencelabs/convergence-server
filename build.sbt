lazy val root = (project in file(".")).
  settings(
    organization:= "com.convergencelabs",
    name := "convergence-server",
    version := "0.1.0",
    scalaVersion := "2.11.7"

  )

// Logging
libraryDependencies += "org.clapper" % "grizzled-slf4j_2.11" % "1.0.2"
libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.3"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.3"
libraryDependencies += "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.3"


libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.4"
libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.11"
libraryDependencies += "com.orientechnologies" % "orientdb-core" % "2.1.2"


val akkaVersion = "2.4.0-RC2"

// Akka Dependencies
libraryDependencies += "com.typesafe.akka" % "akka-actor_2.11" % akkaVersion
libraryDependencies += "com.typesafe.akka" % "akka-testkit_2.11" % akkaVersion
libraryDependencies += "com.typesafe.akka" % "akka-cluster_2.11" % akkaVersion
//libraryDependencies += "com.typesafe.akka" %% "akka-cluster-sharding_2.11" % akkaVersion

libraryDependencies += "io.netty" % "netty-all" % "4.0.31.Final"

// Crypto
libraryDependencies += "org.bitbucket.b_c" % "jose4j" % "0.4.4"
libraryDependencies += "org.bouncycastle" % "bcpkix-jdk15on" % "1.52"


// Testing Dependencies
libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.5" % "test"
libraryDependencies += "org.mockito" % "mockito-all" % "2.0.2-beta"  % "test"
libraryDependencies += "junit" % "junit" % "4.12" % "test"