import sbt._

object Dependencies {
  
  val TestSpecifier = "test,it,e2e"
  
  // Versions
  object Versions {
    val akka      = "2.4.0"
    val orientDb  = "2.1.6"
    val log4j     = "2.4.1"
  }

  object Compile {

    // Akka
    val akkaActor          = "com.typesafe.akka"        % "akka-actor_2.11"               % Versions.akka               // Apache 2.0
    val akkaCluster        = "com.typesafe.akka"        % "akka-cluster_2.11"             % Versions.akka               // Apache 2.0
    val akkaSlf4j          = "com.typesafe.akka"        % "akka-slf4j_2.11"               % Versions.akka              // Apache 2.0
    val akkaCore = Seq(akkaActor, akkaCluster, akkaSlf4j)
    
    val akkaHttp           = "com.typesafe.akka"        % "akka-http-experimental_2.11"   % "1.0"                       // Apache 2.0
    val akkaStream         = "com.typesafe.akka"        % "akka-stream-experimental_2.11" % "1.0"                       // Apache 2.0
    
    // Orient DB Dependencies
    val orientDbClient     = "com.orientechnologies"    % "orientdb-client"               % Versions.orientDb           // Apache 2.0
    val orientDbCore       = "com.orientechnologies"    % "orientdb-core"                 % Versions.orientDb           // Apache 2.0
    val orientDbEnterprise = "com.orientechnologies"    % "orientdb-enterprise"           % Versions.orientDb           // Apache 2.0
    val orientDb = Seq(orientDbClient, orientDbCore, orientDbEnterprise)
  
    // Logging
    val grizzledSlf4j      = "org.clapper"              % "grizzled-slf4j_2.11"           % "1.0.2"                     // BSD
    val log4jSlf4J         = "org.apache.logging.log4j" % "log4j-slf4j-impl"              % Versions.log4j              // Apache 2.0
    val log4jApi           = "org.apache.logging.log4j" % "log4j-api"                     % Versions.log4j              // Apache 2.0
    val log4jCore          = "org.apache.logging.log4j" % "log4j-core"                    % Versions.log4j              // Apache 2.0
    val loggingAll = Seq(grizzledSlf4j, log4jSlf4J, log4jApi, log4jCore)
  
    // Crypto
    val jose4j             = "org.bitbucket.b_c"        % "jose4j"                        % "0.4.4"                     // Apache 2.0
    val bouncyCastle       = "org.bouncycastle"         % "bcpkix-jdk15on"                % "1.52"                      // MIT
    val scrypt             = "com.lambdaworks"          % "scrypt"                        % "1.4.0"                     // Apache 2.0
  
    // HTTP / Websockets
    val netty              = "io.netty"                 % "netty-all"                     % "4.0.31.Final"              // Apache 2.0
    val javaWebsockets     = "org.java-websocket"       % "Java-WebSocket"                % "1.3.0"                     // MIT
  
    //Command Line Parser
    val scallop            = "org.rogach"               % "scallop_2.11"                  % "0.9.5"                     // MIT
  
    // MISC  
    val commonsLang        = "org.apache.commons"       % "commons-lang3"                 % "3.4"                       // Apache 2.0
    val json4s             = "org.json4s"               % "json4s-jackson_2.11"           % "3.3.0"                     // Apache 2.0
  }
  
  object Test {
    // metrics, measurements, perf testing
    val metrics            = "com.codahale.metrics"     % "metrics-core"                  % "3.0.2"            % "test,it,e2e" // ApacheV2
    val metricsJvm         = "com.codahale.metrics"     % "metrics-jvm"                   % "3.0.2"            % "test,it,e2e" // ApacheV2
    val latencyUtils       = "org.latencyutils"         % "LatencyUtils"                  % "1.0.3"            % "test,it,e2e" // Free BSD
    val hdrHistogram       = "org.hdrhistogram"          % "HdrHistogram"                  % "1.1.4"            % "test,it,e2e" // CC0
    val metricsAll         = Seq(metrics, metricsJvm, latencyUtils, hdrHistogram)
      
    // Testing Dependencies
    val akkaTestKit       = "com.typesafe.akka"           % "akka-testkit_2.11"            % Versions.akka       % TestSpecifier // Apache 2.0
    val akkaMockScheduler = "com.miguno.akka"             % "akka-mock-scheduler_2.11"     % "0.4.0"             % TestSpecifier // Apache 2.0
    val scalatest         = "org.scalatest"               % "scalatest_2.11"               % "2.2.5"             % TestSpecifier // Apaceh 2.0
    val mockito           = "org.mockito"                 % "mockito-all"                  % "2.0.2-beta"        % TestSpecifier // MIT
    val junit             = "junit"                       % "junit"                        % "4.12"              % TestSpecifier //EPL 1.0
    val testingCore = Seq(scalatest, mockito, junit)
    val testingAkka = testingCore ++ Seq(akkaTestKit, akkaMockScheduler)
  }
}