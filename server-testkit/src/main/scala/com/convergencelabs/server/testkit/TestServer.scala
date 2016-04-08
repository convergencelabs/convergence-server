package com.convergencelabs.server.testkit

import java.io.File
import scala.annotation.varargs
import com.convergencelabs.server.BackendNode
import com.convergencelabs.server.frontend.realtime.ConvergenceRealTimeFrontend
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
import com.convergencelabs.server.frontend.rest.ConvergenceRestFrontEnd

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
  
  var realTimeFrontEnd: ConvergenceRealTimeFrontend = _
  var realTimeFrontEndSystem: ActorSystem = _
  
  var restFrontEndSystem: ActorSystem = _
  var restFrontEnd: ConvergenceRestFrontEnd = _
  
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

    realTimeFrontEndSystem = startupCluster(2554, "realTimeFrontEndSystem", configFile)
    realTimeFrontEnd = new ConvergenceRealTimeFrontend(realTimeFrontEndSystem, "0.0.0.0", 8080)
    realTimeFrontEnd.start()
    
    restFrontEndSystem = startupCluster(2555, "restFrontend", configFile)
    restFrontEnd = new ConvergenceRestFrontEnd("0.0.0.0", 8081, restFrontEndSystem)
    restFrontEnd.start()

    logger.info("Test Server started.")
  }
  
  def stop(): Unit = {
    backendSystem.terminate()
    backend.stop()
    
    realTimeFrontEndSystem.terminate()
    realTimeFrontEnd.stop()
    
    restFrontEndSystem.terminate()
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