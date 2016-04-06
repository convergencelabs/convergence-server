package com.convergencelabs.server.testkit

import java.io.File

import scala.annotation.varargs

import com.convergencelabs.server.BackendNode
import com.convergencelabs.server.frontend.realtime.ConvergenceRealtimeFrontend
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.typesafe.config.ConfigFactory

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.InitialStateAsEvents
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.UnreachableMember
import grizzled.slf4j.Logging

object TestServer {
  def main(args: Array[String]): Unit = {
    val server = new TestServer(
      "test-server/mono-server-application.conf",
      Map(
        "convergence" -> "test-server/convergence.json.gz",
        "namespace1-domain1" -> "test-server/domain.json.gz"))
    server.start()
  }
}

class TestServer(
  configFile: String,
  databases: Map[String, String])
    extends Logging {

  var backend: BackendNode = _
  var backendSystem: ActorSystem = _
  
  var frontEnd1: ConvergenceRealtimeFrontend = _
  var frontEndSystem1: ActorSystem = _
  
  var frontEnd2: ConvergenceRealtimeFrontend = _
  var frontEndSystem2: ActorSystem = _
  
  def start(): Unit = {
    logger.info("Test Server starting up")
    OLogManager.instance().setConsoleLevel("WARNING")

    // Set Up OrientDB database
    databases.foreach { case (id, file) => importDatabase(id, file) }

    // This is the cluster seed that all of the other systems will check in to.
    // we don't need to deploy anything to it.  It just needs to be there.
    val cluserSeed = startupCluster(2551, "seed", configFile)

    // We have a single backend system.
    backendSystem = startupCluster(2553, "domainManager", configFile)
    backend = new BackendNode(backendSystem)
    backend.start()

    frontEndSystem1 = startupCluster(2554, "realtimeFrontend1", configFile)
    frontEnd1 = new ConvergenceRealtimeFrontend(frontEndSystem1, 8080)
    frontEnd1.start()

//    frontEndSystem2 = startupCluster(2555, "realtimeFrontend2", configFile)
//    frontEnd2 = new ConvergenceRealtimeFrontend(frontEndSystem2, 8081)
//    frontEnd2.start()

    logger.info("Test Server started.")
  }
  
  def stop(): Unit = {
    backendSystem.terminate()
    backend.stop()
    
    frontEndSystem1.terminate()
    frontEnd1.stop()
    
    frontEndSystem2.terminate()
    frontEnd2.stop()
  }

  def importDatabase(dbName: String, importFile: String): Unit = {
    val db = new ODatabaseDocumentTx(s"memory:$dbName")
    db.activateOnCurrentThread()
    db.create()

    val dbImport = new ODatabaseImport(db, importFile, new OCommandOutputListener() { def onMessage(message: String) {} })
    dbImport.importDatabase()
    dbImport.close()

    db.getMetadata.reload()
  }

  def startupCluster(port: Int, role: String, configFile: String): ActorSystem = {
    // Override the configuration of the port
    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(ConfigFactory.parseString(s"akka.cluster.roles = [$role]")).
      withFallback(ConfigFactory.parseFile(new File(configFile)))

    // Create an Akka system
    val system = ActorSystem("Convergence", config)
    // Create an actor that handles cluster domain events
    system.actorOf(Props[SimpleClusterListener], name = "clusterListener")
    system
  }
}

private class SimpleClusterListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart 
  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember])
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) => {
      log.debug(s"Member with role '${member.roles}' is Up: ${member.address}")
    }
    case UnreachableMember(member) => {
      log.debug("Member detected as unreachable: {}", member)
    }
    case MemberRemoved(member, previousStatus) => {
      log.debug("Member is Removed: {} after {}", member.address, previousStatus)
    }
    case _: MemberEvent => // ignore
  }
}