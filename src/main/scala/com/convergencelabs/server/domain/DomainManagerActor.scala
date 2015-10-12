package com.convergencelabs.server.domain

import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.PersistenceProvider
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.PoisonPill
import akka.actor.Props
import akka.cluster.ClusterEvent._
import akka.cluster.Cluster
import akka.actor.Scheduler

object DomainManagerActor {
  def props(
    convergencePersistence: PersistenceProvider,
    protocolConfig: ProtocolConfiguration): Props = Props(
    new DomainManagerActor(
      convergencePersistence,
      protocolConfig))
}

class DomainManagerActor(
  private[this] val convergencePersistence: PersistenceProvider,
  private[this] val protocolConfig: ProtocolConfiguration)
    extends Actor with ActorLogging {

  log.debug("DomainManagerActor starting up with address: " + self.path)

  private[this] val cluster = Cluster(context.system)
  private[this] implicit val ec = context.dispatcher
  
  private[this] val domainConfigStore = convergencePersistence.domainConfigStore

  // FIXME pull from config
  private[this] val domainShutdownDelay = new FiniteDuration(10, TimeUnit.SECONDS)

  private[this] val actorsToDomainFqn = mutable.HashMap[ActorRef, DomainFqn]()
  private[this] val domainFqnToActor = mutable.HashMap[DomainFqn, ActorRef]()
  private[this] val queuedHahdshakeRequests = mutable.Map[DomainFqn, ListBuffer[HandshakeRequestRecord]]()
  private[this] val shudownRequests = mutable.Map[DomainFqn, Cancellable]()

  log.debug("DomainManager started.")

  def receive = {
    case handshake: HandshakeRequest => onHandshakeRequest(handshake)
    case shutdownRequest: DomainShutdownRequest => onDomainShutdownRequest(shutdownRequest)
    case shutdownApproval: DomainShutdownApproval => onDomainShutdownApproval(shutdownApproval)
    case message => unhandled(message)
  }

  private[this] def onHandshakeRequest(request: HandshakeRequest): Unit = {
    val domainFqn = request.domainFqn
    if (!domainConfigStore.domainExists(domainFqn)) {
      sender() ! HandshakeFailure("domain_does_not_exists", "The domain does not exist")
      return
    }

    if (!this.domainFqnToActor.contains(domainFqn)) {
      log.debug("Client connected to unloaded domain '{}', loading it.", domainFqn)
      handleClientOpeningClosedDomain(domainFqn, request)
    } else {
      log.debug("Client connected to loaded domain '{}'.", domainFqn)
      // If this domain was going to be closing, cancel the close request.
      if (shudownRequests.contains(domainFqn)) {
        log.debug(s"Canceling request to close domain: ${domainFqn}", domainFqn)
        shudownRequests(domainFqn).cancel()
        shudownRequests.remove(domainFqn)
      }

      domainFqnToActor(domainFqn) forward request
    }
  }

  private[this] def handleClientOpeningClosedDomain(domainFqn: DomainFqn, request: HandshakeRequest): Unit = {
    if (domainConfigStore.domainExists(domainFqn)) {
      // This only works because this is synchronous.

      // FIXME I don't think it is for sure that the actor will be up and running at this point.
      openClosedDomain(domainFqn)
      domainFqnToActor(domainFqn) forward request
    } else {
      sender ! HandshakeFailure("domain_does_not_exists", "The domain does not exist")
    }
  }

  private[this] def openClosedDomain(domainFqn: DomainFqn): Unit = {
    val domainPersistence = convergencePersistence.getDomainPersitenceProvider(domainFqn)
    val domainConfig = convergencePersistence.domainConfigStore.getDomainConfig(domainFqn)

    val domainActor = context.actorOf(DomainActor.props(
      self,
      domainConfig,
      domainPersistence,
      convergencePersistence.convergenceConfigStore,
      protocolConfig,
      domainShutdownDelay))

    actorsToDomainFqn(domainActor) = domainFqn
    domainFqnToActor(domainFqn) = domainActor
  }

  private[this] def onDomainShutdownRequest(request: DomainShutdownRequest): Unit = {
    val shutdownTask = context.system.scheduler.scheduleOnce(
      domainShutdownDelay,
      self,
      DomainShutdownApproval(request.domainFqn))
  }

  private[this] def onDomainShutdownApproval(shutdownApproval: DomainShutdownApproval): Unit = {
    // If the map doesn't contain the request, that means that it was canceled
    if (shudownRequests.contains(shutdownApproval.domainFqn)) {
      val domainFqn = shutdownApproval.domainFqn
      log.debug("Shutting down domain '{}'", domainFqn)
      shudownRequests.remove(shutdownApproval.domainFqn)
      val domainActor = domainFqnToActor(domainFqn)
      domainFqnToActor.remove(domainFqn)
      actorsToDomainFqn.remove(domainActor)
      domainActor ! PoisonPill
    }
  }

  override def postStop() {
    log.debug("DomainManager shutdown.")
  }
}

private case class HandshakeRequestRecord(asker: ActorRef, request: HandshakeRequest)
private case class DomainShutdownApproval(domainFqn: DomainFqn)