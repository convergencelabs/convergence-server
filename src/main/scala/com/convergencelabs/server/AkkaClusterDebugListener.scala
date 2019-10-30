package com.convergencelabs.server

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp, UnreachableMember}

/**
 * A helper [[Actor]] that will listen to Akka Cluster events and log debug
 * messages to the console.
 *
 * @param cluster The Akka [[Cluster]] to listen to.
 */
class AkkaClusterDebugListener(cluster: Cluster) extends Actor with ActorLogging {
  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive: Receive = {
    case MemberUp(member) =>
      log.debug(s"Akka Cluster Member with role '${member.roles}' is Up: ${member.address}")
    case UnreachableMember(member) =>
      log.debug("Akka Cluster Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.debug("Akka Cluster Member is Removed: {} after {}", member.address, previousStatus)
    case msg: MemberEvent =>
      log.debug(msg.toString)
  }
}
