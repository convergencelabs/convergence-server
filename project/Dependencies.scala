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

import sbt._

object Dependencies {

  // Versions
  object Versions {
    val akka      = "2.6.14"
    val akkaHttp  = "10.2.4"
    val orientDb  = "3.0.37"
    val log4j     = "2.14.1"
    val jackson   = "2.11.4"
  }

  object Compile {
    // Convergence
    val convergenceProto   = "com.convergencelabs"      %% "convergence-proto-scala"      % "1.0.0-rc.6"                // Apache 2.0

    // Protobuf
    val scalapb            = "com.thesamet.scalapb"     %% "scalapb-runtime"              % "0.11.1"                    // Apache 2.0

    // Akka
    val akkaActor          = "com.typesafe.akka"        %% "akka-actor"                   % Versions.akka               // Apache 2.0
    val akkaActorTyped     = "com.typesafe.akka"        %% "akka-actor-typed"             % Versions.akka               // Apache 2.0
    val akkaStream         = "com.typesafe.akka"        %% "akka-stream"                  % Versions.akka               // Apache 2.0
    val akkaClusterTyped   = "com.typesafe.akka"        %% "akka-cluster-typed"           % Versions.akka               // Apache 2.0
    val akkaShardingTyped  = "com.typesafe.akka"        %% "akka-cluster-sharding-typed"  % Versions.akka               // Apache 2.0
    val akkaSlf4j          = "com.typesafe.akka"        %% "akka-slf4j"                   % Versions.akka               // Apache 2.0
    val akkaPersistence    = "com.typesafe.akka"        %% "akka-persistence"             % Versions.akka               // Apache 2.0
    val akkaJackson        = "com.typesafe.akka"        %% "akka-serialization-jackson"   % Versions.akka               // Apache 2.0

    val akkaCore: Seq[ModuleID] = Seq(akkaActor, akkaActorTyped, akkaClusterTyped, akkaShardingTyped, akkaStream, akkaSlf4j, akkaPersistence, akkaJackson)

    val akkaHttp           = "com.typesafe.akka"        %% "akka-http"                    % Versions.akkaHttp           // Apache 2.0
    val akkaHttpTest       = "com.typesafe.akka"        %% "akka-http-testkit"            % Versions.akkaHttp           // Apache 2.0

    val akkaHttpJson4s     = "de.heikoseeberger"        %% "akka-http-json4s"             % "1.27.0"                    // Apache 2.0
    val akkaHttpCors       = "ch.megard"                %% "akka-http-cors"               % "0.4.3"                     // Apache 2.0

    // Orient DB Dependencies
    val orientDbClient     = "com.orientechnologies"    % "orientdb-client"              % Versions.orientDb            // Apache 2.0
    val orientDbCore       = "com.orientechnologies"    % "orientdb-core"                % Versions.orientDb            // Apache 2.0
    val orientDbServer     = "com.orientechnologies"    % "orientdb-server"              % Versions.orientDb            // Apache 2.0
    val orientDbStudio     = "com.orientechnologies"    % "orientdb-studio"              % Versions.orientDb            // Apache 2.0
    val orientDb: Seq[ModuleID] = Seq(orientDbClient, orientDbCore, orientDbServer, orientDbStudio)

    // Logging
    val grizzledSlf4j      = "org.clapper"              %% "grizzled-slf4j"               % "1.3.4"                     // BSD
    val log4jSlf4J         = "org.apache.logging.log4j" % "log4j-slf4j-impl"              % Versions.log4j              // Apache 2.0
    val log4jApi           = "org.apache.logging.log4j" % "log4j-api"                     % Versions.log4j              // Apache 2.0
    val log4jCore          = "org.apache.logging.log4j" % "log4j-core"                    % Versions.log4j              // Apache 2.0
	  val log4jJul           = "org.apache.logging.log4j" % "log4j-jul"                     % Versions.log4j              // Apache 2.0
    val loggingAll: Seq[ModuleID] = Seq(grizzledSlf4j, log4jSlf4J, log4jApi, log4jCore, log4jJul)

    // Crypto
    val jose4j             = "org.bitbucket.b_c"        % "jose4j"                        % "0.6.5"                     // Apache 2.0
    val bouncyCastle       = "org.bouncycastle"         % "bcpkix-jdk15on"                % "1.63"                      // MIT
    val scrypt             = "com.lambdaworks"          % "scrypt"                        % "1.4.0"                     // Apache 2.0

    // HTTP / Websockets
    val netty              = "io.netty"                 % "netty-all"                     % "4.0.31.Final"              // Apache 2.0
    val javaWebsockets     = "org.java-websocket"       % "Java-WebSocket"                % "1.3.0"                     // MIT

    //Command Line Parser
    val scallop            = "org.rogach"               %% "scallop"                        % "3.4.0"                   // MIT

    // MISC
    val commonsLang        = "org.apache.commons"       % "commons-lang3"                   % "3.4"                     // Apache 2.0
    val json4s             = "org.json4s"               %% "json4s-jackson"                 % "3.6.8"                   // Apache 2.0
    val json4sExt          = "org.json4s"               %% "json4s-ext"                     % "3.6.8"                   // Apache 2.0
    val jacksonDatabind    = "com.fasterxml.jackson.core" % "jackson-databind"              % Versions.jackson          // Apache 2.0
    val jacksonYaml        = "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % Versions.jackson          // Apache 2.0
    val jacksonCbor        = "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % Versions.jackson          // Apache 2.0
    val jacksonJdk8        = "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"     % Versions.jackson          // Apache 2.0
    val jacksonJsr310      = "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"   % Versions.jackson          // Apache 2.0
    val jacksonScala       = "com.fasterxml.jackson.module" %% "jackson-module-scala"       % Versions.jackson          // Apache 2.0
    val parboiled          = "org.parboiled"            %% "parboiled"                      % "2.2.1"                   // Apache 2.0
  }

  object Test {
    // metrics, measurements, perf testing
    val metrics            = "com.codahale.metrics"     % "metrics-core"                  % "3.0.2"                     // Apache 2.0
    val metricsJvm         = "com.codahale.metrics"     % "metrics-jvm"                   % "3.0.2"                     // Apache 2.0
    val latencyUtils       = "org.latencyutils"         % "LatencyUtils"                  % "1.0.3"                     // Free BSD
    val hdrHistogram       = "org.hdrhistogram"          % "HdrHistogram"                 % "1.1.4"                     // CC0
    val metricsAll: Seq[ModuleID] = Seq(metrics, metricsJvm, latencyUtils, hdrHistogram)

    // Testing Dependencies
    val akkaTestKit       = "com.typesafe.akka"           %% "akka-testkit"                % Versions.akka              // Apache 2.0
    val akkaTestKitTyped  = "com.typesafe.akka"           %% "akka-actor-testkit-typed"    % Versions.akka              // Apache 2.0
    val akkaStreamTestKit = "com.typesafe.akka"           %% "akka-stream-testkit"         % Versions.akka
    val akkaHttpTestKit   = "com.typesafe.akka"           %% "akka-http-testkit"           % Versions.akkaHttp
    val akkaMockScheduler = "com.miguno.akka"             %% "akka-mock-scheduler"         % "0.5.5"                    // Apache 2.0
    val scalatest         = "org.scalatest"               %%  "scalatest"                  % "3.1.2"                    // Apache 2.0
    val scalatestMockito  = "org.scalatestplus"           %% "scalatestplus-mockito"       % "1.0.0-M2"                 // Apache 2.0
    val mockito           = "org.mockito"                 % "mockito-all"                  % "2.0.2-beta"               // MIT
    val junit             = "junit"                       % "junit"                        % "4.12"                     // EPL 1.0
    val testingCore: Seq[ModuleID] = Seq(scalatest, scalatestMockito, mockito, junit)
    val testingAkka: Seq[ModuleID] = testingCore ++ Seq(akkaTestKit, akkaMockScheduler, akkaTestKitTyped, akkaHttpTestKit, akkaStreamTestKit)
  }
}
