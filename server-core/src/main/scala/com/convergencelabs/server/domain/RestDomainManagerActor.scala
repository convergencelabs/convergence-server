package com.convergencelabs.server.domain

import java.time.Instant

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.PersistenceProvider
import com.convergencelabs.server.domain.RestDomainActor.Shutdown
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage
import com.convergencelabs.server.domain.RestDomainManagerActor.ScheduledShutdown
import com.convergencelabs.server.domain.RestDomainManagerActor.ShutdownDomain
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.cluster.Cluster

object RestDomainManagerActor {
  def props(dbPool: OPartitionedDatabasePool): Props = Props(new RestDomainManagerActor(dbPool))

  val RelativeActorPath = "restDomainManager"

  case class DomainMessage(domainFqn: DomainFqn, message: Any)
  case class ScheduledShutdown(cancellable: Cancellable, lastMessageTime: Instant)
  case class ShutdownDomain(domainFqn: DomainFqn)
}

class RestDomainManagerActor(dbPool: OPartitionedDatabasePool)
    extends Actor with ActorLogging {

  private[this] val cluster = Cluster(context.system)
  private[this] implicit val ec = context.dispatcher

  private[this] val persistenceProvider = new PersistenceProvider(dbPool)
  private[this] val domainStore = persistenceProvider.domainStore

  private[this] val domainShutdownDelay2 =
    context.system.settings.config.getDuration("convergence.rest-domain-shutdown-delay")
  private[this] val domainShutdownDelay = Duration.fromNanos(domainShutdownDelay2.toNanos())

  private[this] val domainFqnToActor = mutable.HashMap[DomainFqn, ActorRef]()
  private[this] val scheduledShutdowns = mutable.Map[DomainFqn, ScheduledShutdown]()

  def receive: Receive = {
    case message: DomainMessage => onDomainMessage(message)
    case message: ShutdownDomain => onShutdownDomain(message)
    case message: Any => unhandled(message)
  }

  private[this] def onDomainMessage(message: DomainMessage): Unit = {
    val domainFqn = message.domainFqn
    if (domainFqnToActor.contains(domainFqn)) {
      scheduledShutdowns(domainFqn).cancellable.cancel()
      val shutdownTask = context.system.scheduler.scheduleOnce(domainShutdownDelay, self, ShutdownDomain(domainFqn))
      scheduledShutdowns(domainFqn) = ScheduledShutdown(shutdownTask, Instant.now())
      domainFqnToActor(domainFqn) forward message.message
    } else {
      domainStore.getDomainByFqn(domainFqn) match {
        case Success(Some(domain)) =>
          val domainActor = context.actorOf(RestDomainActor.props(domain.domainFqn))
          domainFqnToActor(domain.domainFqn) = domainActor
          val shutdownTask = context.system.scheduler.scheduleOnce(domainShutdownDelay, self, ShutdownDomain(domainFqn))
          scheduledShutdowns(domainFqn) = ScheduledShutdown(shutdownTask, Instant.now())
          domainFqnToActor(domainFqn) forward message.message
        case Success(None) =>
          sender ! akka.actor.Status.Failure(new IllegalStateException("Domain does not exist"))
        case Failure(cause) =>
          akka.actor.Status.Failure(cause)
      }
    }
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
