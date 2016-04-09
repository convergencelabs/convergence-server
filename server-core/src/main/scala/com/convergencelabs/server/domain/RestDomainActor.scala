package com.convergencelabs.server.domain

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Cancellable
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.RestDomainActor._
import akka.pattern.gracefulStop
import com.convergencelabs.server.datastore.UserStoreActor.UserStoreRequest
import com.convergencelabs.server.datastore.UserStoreActor
import akka.actor.PoisonPill
import akka.dispatch.Futures

object RestDomainActor {
  def props(domainFqn: DomainFqn): Props = Props(new RestDomainActor(domainFqn))

  case class DomainMessage(domainFqn: DomainFqn, message: Any)
  case object Shutdown

  val GracefulStopWaitTime: FiniteDuration = new FiniteDuration(30, TimeUnit.SECONDS)
}

class RestDomainActor(domainFqn: DomainFqn) extends Actor with ActorLogging {

  private[this] var persistenceProvider: DomainPersistenceProvider = _
  private[this] implicit val ec = context.dispatcher
  private[this] val userStoreActor: ActorRef = context.actorOf(UserStoreActor.props(domainFqn))

  def receive: Receive = {
    case message: UserStoreRequest => userStoreActor forward message
    case Shutdown                  => shutdown()
    case message: Any              => unhandled(message)
  }

  def shutdown(): Unit = {
    gracefulStop(userStoreActor, GracefulStopWaitTime, PoisonPill).onComplete(_ => context.stop(self))
  }
}
