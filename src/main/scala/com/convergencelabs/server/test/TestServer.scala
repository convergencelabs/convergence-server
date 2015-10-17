package com.convergencelabs.server.test

import java.io.File
import com.convergencelabs.server.frontend.realtime.ConvergenceRealtimeFrontend
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
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.common.log.OLogManager
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.UnreachableMember
import com.convergencelabs.server.BackendNode
import grizzled.slf4j.Logging

object TestServer {
  def main(args: Array[String]): Unit = {
    var server = new TestServer(
      "test-server/mono-server-application.conf",
      "test-server/convergence.json",
      "convergence")
    server.start()
  }
}

class TestServer(
    configFile: String,
    dbFile: String,
    dbName: String) extends Logging {

  def start(): Unit = {
    logger.info("Test Server starting up")
    OLogManager.instance().setConsoleLevel("WARNING")
    
    // Set Up OrientDB
    importDatabase(dbName, dbFile)
    importDatabase("t1", "test-server/domain-test-db.gz")

    // This is the cluster seed that all of the other systems will check in to.
    // we don't need to deploy anything to it.  It just needs to be there.
    val cluserSeed = startupCluster(2551, "seed", configFile)

    // We have a single backend system.
    val backendNodeSystem = startupCluster(2553, "domainManager", configFile)
    val backend = new BackendNode(backendNodeSystem)
    backend.start()

    val realtimeFrontEndSystem1 = startupCluster(2554, "realtimeFrontend1", configFile)
    val realtimeServer1 = new ConvergenceRealtimeFrontend(realtimeFrontEndSystem1, 8080)
    realtimeServer1.start()

    val realtimeFrontEndSystem2 = startupCluster(2555, "realtimeFrontend2", configFile)
    val realtimeServer2 = new ConvergenceRealtimeFrontend(realtimeFrontEndSystem2, 8081)
    realtimeServer2.start()
    
    logger.info("Test Server started.")
  }
  
  def importDatabase(dbName: String, importFile: String): Unit = {
    val db = new ODatabaseDocumentTx(s"memory:$dbName")
    db.activateOnCurrentThread()
    db.create()

    val dbImport = new ODatabaseImport(db, importFile, new OCommandOutputListener() { def onMessage(message: String) {} })
    dbImport.importDatabase()
    dbImport.close()
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