package com.convergencelabs.server.domain

import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import com.convergencelabs.server.datastore.PersistenceProvider
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.PoisonPill
import akka.actor.Props
import akka.cluster.Cluster
import akka.actor.Scheduler
import scala.util.Success
import scala.util.Failure
import com.convergencelabs.server.domain.RestDomainActor.DomainMessage
import com.convergencelabs.server.domain.RestDomainManagerActor.ShutdownDomain
import com.convergencelabs.server.domain.RestDomainManagerActor.ScheduledShutdown
import java.time.Instant
import java.time.Duration
import java.time.temporal.TemporalUnit
import java.time.temporal.ChronoUnit
import com.convergencelabs.server.domain.RestDomainActor.Shutdown

object RestDomainManagerActor {
  val RelativeActorPath = "restDomainManager"

  def props(
    convergencePersistence: PersistenceProvider): Props = Props(
    new RestDomainManagerActor(convergencePersistence))

  case class ScheduledShutdown(cancellable: Cancellable, lastMessageTime: Instant)

  case class ShutdownDomain(domainFqn: DomainFqn)
}

class RestDomainManagerActor(convergencePersistence: PersistenceProvider)
    extends Actor with ActorLogging {

  private[this] val cluster = Cluster(context.system)
  private[this] implicit val ec = context.dispatcher

  private[this] val domainStore = convergencePersistence.domainStore
  private[this] val domainShutdownDelay = new FiniteDuration(5, TimeUnit.MINUTES)
  private[this] val domainShutdownDelay2 = Duration.of(5, ChronoUnit.MINUTES)

  private[this] val domainFqnToActor = mutable.HashMap[DomainFqn, ActorRef]()
  private[this] val scheduledShutdowns = mutable.Map[DomainFqn, ScheduledShutdown]()

  def receive: Receive = {
    case message: DomainMessage  => onDomainMessage(message)
    case message: ShutdownDomain => onShutdownDomain(message)
    case message: Any            => unhandled(message)
  }

  private[this] def onDomainMessage(message: DomainMessage): Unit = {
    val domainFqn = message.domainFqn

    if (domainFqnToActor.contains(domainFqn)) {
      scheduledShutdowns(domainFqn).cancellable.cancel()
      val shutdownTask = context.system.scheduler.scheduleOnce(domainShutdownDelay, self, ShutdownDomain(domainFqn))
      scheduledShutdowns(domainFqn) = ScheduledShutdown(shutdownTask, Instant.now())
    } else {
      domainStore.getDomainByFqn(domainFqn) match {
        case Success(None) => ??? //Handle Error: domain does not exist
        case Success(Some(domain)) => {
          val domainActor = context.actorOf(RestDomainActor.props(domain.domainFqn))
          domainFqnToActor(domain.domainFqn) = domainActor
          val shutdownTask = context.system.scheduler.scheduleOnce(domainShutdownDelay, self, ShutdownDomain(domainFqn))
          scheduledShutdowns(domainFqn) = ScheduledShutdown(shutdownTask, Instant.now())
        }
        case Failure(cause) => {
          ???
          //          sender ! HandshakeFailure("unknown", "unknown error handshaking")
        }
      }
    }

    domainFqnToActor(domainFqn) forward message.message
  }

  private[this] def onShutdownDomain(message: ShutdownDomain): Unit = {
    val domainFqn = message.domainFqn
    val ScheduledShutdown(shutdownTask, lastMessageTime) = scheduledShutdowns(domainFqn)
    if (lastMessageTime.plus(domainShutdownDelay2).isBefore(Instant.now())) {
      scheduledShutdowns.remove(domainFqn)
      val domainActor = domainFqnToActor(domainFqn)
      domainFqnToActor.remove(domainFqn)
      domainActor ! Shutdown

    }
  }

  override def postStop(): Unit = {
    log.debug("RestDomainManager shutdown.")
  }
}

