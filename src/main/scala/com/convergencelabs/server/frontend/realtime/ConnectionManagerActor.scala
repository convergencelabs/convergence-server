package com.convergencelabs.server.frontend.realtime

import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.convergencelabs.server.domain.DomainFqn
import akka.cluster.Member
import akka.actor.RootActorPath
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.util.Success
import scala.util.Failure
import akka.actor.ActorRef
import com.convergencelabs.server.domain.HandshakeRequest
import com.convergencelabs.server.frontend.realtime.proto.HandshakeRequestMessage
import java.util.UUID
import com.convergencelabs.server.ProtocolConfiguration
import scala.collection.mutable
import com.convergencelabs.server.domain.HandshakeRequest

object ConnectionManagerActor {
  def props(protocolConfig: ProtocolConfiguration): Props = Props(
    new ConnectionManagerActor(protocolConfig))
}

class ConnectionManagerActor(
  protocolConfig: ProtocolConfiguration)
    extends Actor with ActorLogging {

  private[this] val cluster = Cluster(context.system)
  private[this] implicit val ec = context.dispatcher
  var domainManagerActor: ActorRef = null

  def receive = {
    case MemberUp(member) if member.hasRole("domainManager") => registerDomainManager(member)
    case DomainManagerRegistration(ref) => {
      domainManagerActor = ref
      context.become(receiveWhenInitialized)
      log.info("Domain Manager Registered")
    }
    case message => unhandled(message)
  }

  def receiveWhenInitialized: Receive = {
    case newSocketEvent: NewSocketEvent => onNewSocketEvent(newSocketEvent)
    case message => unhandled(message)
  }

  private[this] def registerDomainManager(member: Member): Unit = {
    val domainManager = context.actorSelection(RootActorPath(member.address) / "user" / "domainManager")
    val f = domainManager.resolveOne(new FiniteDuration(10, TimeUnit.SECONDS))
    f onComplete {
      case Success(ref) => self ! DomainManagerRegistration(ref)
      case Failure(cause) => cause.printStackTrace()
    }
  }

  private[this] def onNewSocketEvent(newSocketEvent: NewSocketEvent): Unit = {
    val connection = new ProtocolConnection(
      newSocketEvent.socket,
      protocolConfig,
      context.system.scheduler,
      context.dispatcher)

    val clientActor = context.system.actorOf(ClientActor.props(
      domainManagerActor,
      connection,
      newSocketEvent.domainFqn))
  }

  override def preStart(): Unit = {
    cluster.subscribe(self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember])
  }
}

case class DomainManagerRegistration(actor: ActorRef)
case class NewSocketEvent(domainFqn: DomainFqn, socket: ConvergenceServerSocket)

