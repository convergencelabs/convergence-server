package com.convergencelabs.server.domain

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceManager

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.PoisonPill
import akka.actor.Props
import akka.cluster.Cluster

object DomainManagerActor {
  val RelativeActorPath = "domainManager"

  def props(
    domainStore: DomainStore,
    protocolConfig: ProtocolConfiguration,
    persistenceManager: DomainPersistenceManager): Props = Props(
    new DomainManagerActor(
      domainStore,
      protocolConfig,
      persistenceManager))
}

class DomainManagerActor(
  private[this] val domainStore: DomainStore,
  private[this] val protocolConfig: ProtocolConfiguration,
  private[this] val persistenceManager: DomainPersistenceManager)
    extends Actor with ActorLogging {

  log.debug("DomainManagerActor starting up with address: " + self.path)

  private[this] val cluster = Cluster(context.system)
  private[this] implicit val ec = context.dispatcher

  private[this] val domainShutdownDelay = Duration.fromNanos(
    context.system.settings.config.getDuration("convergence.domain-shutdown-delay").toNanos)

  private[this] val actorsToDomainFqn = mutable.HashMap[ActorRef, DomainFqn]()
  private[this] val domainFqnToActor = mutable.HashMap[DomainFqn, ActorRef]()
  private[this] val shudownRequests = mutable.Map[DomainFqn, Cancellable]()

  log.debug("DomainManager started.")

  def receive: Receive = {
    case handshake: HandshakeRequest => onHandshakeRequest(handshake)
    case shutdownRequest: DomainShutdownRequest => onDomainShutdownRequest(shutdownRequest)
    case shutdownApproval: DomainShutdownApproval => onDomainShutdownApproval(shutdownApproval)
    case message: Any => unhandled(message)
  }

  private[this] def onHandshakeRequest(request: HandshakeRequest): Unit = {
    val domainFqn = request.domainFqn
    if (this.domainFqnToActor.contains(domainFqn)) {
      log.debug("Client connected to loaded domain '{}'.", domainFqn)
      // If this domain was going to be closing, cancel the close request.
      if (shudownRequests.contains(domainFqn)) {
        log.debug(s"Canceling request to close domain: ${domainFqn}")
        shudownRequests(domainFqn).cancel()
        shudownRequests.remove(domainFqn)
      }
      domainFqnToActor(domainFqn) forward request
    } else {
      handleClientOpeningClosedDomain(domainFqn, request)
    }
  }

  private[this] def handleClientOpeningClosedDomain(domainFqn: DomainFqn, request: HandshakeRequest): Unit = {
    openClosedDomain(domainFqn) match {
      case Success(None) =>
        log.debug("Client connected to non-existent domain '{}'", domainFqn)
        sender ! HandshakeFailure("domain_does_not_exists", "The domain does not exist")
      case Success(Some(domainActor)) =>
        log.debug("Client connected to unloaded domain '{}', loaded it.", domainFqn)
        domainActor forward request
      case Failure(cause) =>
        log.error(cause, "Unknown error handshaking")
        sender ! HandshakeFailure("unknown", "unknown error handshaking")
    }
  }

  private[this] def openClosedDomain(domainFqn: DomainFqn): Try[Option[ActorRef]] = {
    domainStore.getDomainByFqn(domainFqn) flatMap {
      case Some(domainConfig) => {
        val domainActor = context.actorOf(DomainActor.props(
          self,
          domainConfig.domainFqn,
          protocolConfig,
          domainShutdownDelay,
          persistenceManager))
        actorsToDomainFqn(domainActor) = domainFqn
        domainFqnToActor(domainFqn) = domainActor
        Success(Some(domainActor))
      }
      case None => Success(None)
    }
  }

  private[this] def onDomainShutdownRequest(request: DomainShutdownRequest): Unit = {
    log.debug(s"Shutdown request received for domain: ${request.domainFqn}")
    val shutdownTask = context.system.scheduler.scheduleOnce(
      domainShutdownDelay,
      self,
      DomainShutdownApproval(request.domainFqn))
    this.shudownRequests(request.domainFqn) = shutdownTask
  }

  private[this] def onDomainShutdownApproval(approval: DomainShutdownApproval): Unit = {
    // If the map doesn't contain the request, that means that it was canceled
    if (shudownRequests.contains(approval.domainFqn)) {
      log.debug(s"Shutdown request approved for domain: ${approval.domainFqn}")
      val domainFqn = approval.domainFqn
      log.debug("Shutting down domain '{}'", domainFqn)
      shudownRequests.remove(approval.domainFqn)
      val domainActor = domainFqnToActor(domainFqn)
      domainFqnToActor.remove(domainFqn)
      actorsToDomainFqn.remove(domainActor)
      domainActor ! PoisonPill
    }
  }

  override def postStop(): Unit = {
    log.debug("DomainManager shutdown.")
  }
}

private case class DomainShutdownApproval(domainFqn: DomainFqn)
