package com.convergencelabs.server

import java.io.File

import com.convergencelabs.server.datastore.DomainConfigurationStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.DomainManagerActor
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

object MonoServer {
  def main(args: Array[String]): Unit = {
    val seed1 = startupCluster(2551, "seed")

    val configStore: DomainConfigurationStore = null 
    
    val domainManagerSystem = startupCluster(2553, "domainManager")
    val dbPoolManager = domainManagerSystem.actorOf(DomainPersistenceManagerActor.props(configStore), "DatabasePoolManager")
    
    domainManagerSystem.actorOf(DomainManagerActor.props(null, null), "domainManager")
    
    
    val realtimeSystem = startupCluster(2554, "realtimeFrontend")
    val realtimeServer = new ConvergenceRealtimeFrontend(realtimeSystem)
    realtimeServer.start()
  }

  def startupCluster(port: Int, role: String): ActorSystem = {
    // Override the configuration of the port
    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(ConfigFactory.parseString(s"akka.cluster.roles = [$role]")).
      withFallback(ConfigFactory.parseFile(new File("conf/mono-server-application.conf")))

    // Create an Akka system
    val system = ActorSystem("Convergence", config)
    // Create an actor that handles cluster domain events
    system.actorOf(Props[SimpleClusterListener], name = "clusterListener")
    system
  }
}

class SimpleClusterListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart 
  override def preStart(): Unit = {
    //#subscribe
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
    //#subscribe
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) =>
      log.info(s"Member with role '${member.roles}' is Up: ${member.address}")
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}",
        member.address, previousStatus)
    case _: MemberEvent => // ignore
  }
}