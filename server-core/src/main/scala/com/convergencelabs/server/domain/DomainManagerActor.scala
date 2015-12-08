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
import scala.util.Success
import scala.util.Failure
import scala.util.Success

object DomainManagerActor {
  val RelativeActorPath = "domainManager"

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

  private[this] val domainStore = convergencePersistence.domainStore

  // FIXME pull from config
  private[this] val domainShutdownDelay = new FiniteDuration(10, TimeUnit.SECONDS)

  private[this] val actorsToDomainFqn = mutable.HashMap[ActorRef, DomainFqn]()
  private[this] val domainFqnToActor = mutable.HashMap[DomainFqn, ActorRef]()
  private[this] val queuedHahdshakeRequests = mutable.Map[DomainFqn, ListBuffer[HandshakeRequestRecord]]()
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
    domainStore.domainExists(domainFqn) match {
      case Success(false) => sender ! HandshakeFailure("domain_does_not_exists", "The domain does not exist")
      case Success(true) => {
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
      case Failure(cause) => {
        log.error(cause, "Unknown error handshaking")
        sender ! HandshakeFailure("unknown", "unknown error handshaking")
      }
    }
  }

  private[this] def handleClientOpeningClosedDomain(domainFqn: DomainFqn, request: HandshakeRequest): Unit = {
    // This only works because this is synchronous.
    // FIXME I don't think it is for sure that the actor will be up and running at this point.
    openClosedDomain(domainFqn)
    domainFqnToActor(domainFqn) forward request
  }

  private[this] def openClosedDomain(domainFqn: DomainFqn): Unit = {
    domainStore.getDomainByFqn(domainFqn) match {
      case Success(Some(domainConfig)) => {
        val domainActor = context.actorOf(DomainActor.props(
          self,
          domainConfig.domainFqn,
          protocolConfig,
          domainShutdownDelay))

        actorsToDomainFqn(domainActor) = domainFqn
        domainFqnToActor(domainFqn) = domainActor
      }
      case Success(None) => ??? // FIXME need to respond with a failure.
      case Failure(cause) => ??? // FIXME need to respond with a failure.
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

private case class HandshakeRequestRecord(asker: ActorRef, request: HandshakeRequest)
private case class DomainShutdownApproval(domainFqn: DomainFqn)
