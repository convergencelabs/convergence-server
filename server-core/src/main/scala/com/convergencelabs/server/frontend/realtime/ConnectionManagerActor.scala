package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.RootActorPath
import akka.actor.actorRef2Scala
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.InitialStateAsEvents
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.Member

object ConnectionManagerActor {
  def props(startUpListener: ActorRef, protocolConfig: ProtocolConfiguration): Props = Props(
    new ConnectionManagerActor(startUpListener, protocolConfig))
}

class ConnectionManagerActor(
  startUpListener: ActorRef,
  protocolConfig: ProtocolConfiguration)
    extends Actor with ActorLogging {

  private[this] val cluster = Cluster(context.system)
  private[this] implicit val ec = context.dispatcher
  var domainManagerActor: ActorRef = _

  def receive: Receive = {
    case MemberUp(member) if member.hasRole("domainManager") => registerDomainManager(member)
    case DomainManagerRegistration(ref) => {
      domainManagerActor = ref
      context.become(receiveWhenInitialized)
      log.info("Domain Manager Registered")
      startUpListener ! StartUpComplete
    }
    case message: Any => unhandled(message)
  }

  def receiveWhenInitialized: Receive = {
    case newSocketEvent: NewSocketEvent => onNewSocketEvent(newSocketEvent)
    case message: Any => unhandled(message)
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

    // FIXME hardcoded timeout

    val clientActor = context.system.actorOf(ClientActor.props(
      domainManagerActor,
      connection,
      newSocketEvent.domainFqn,
      FiniteDuration(5, TimeUnit.SECONDS)))
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
case object StartUpComplete
