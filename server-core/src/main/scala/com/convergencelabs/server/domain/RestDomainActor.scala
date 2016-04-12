package com.convergencelabs.server.domain

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import com.convergencelabs.server.datastore.CollectionStoreActor
import com.convergencelabs.server.datastore.CollectionStoreActor.CollectionStoreRequest
import com.convergencelabs.server.datastore.UserStoreActor
import com.convergencelabs.server.datastore.UserStoreActor.UserStoreRequest
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.RestDomainActor.GracefulStopWaitTime
import com.convergencelabs.server.domain.RestDomainActor.Shutdown
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.pattern.gracefulStop
import com.convergencelabs.server.datastore.ModelStoreActor.ModelStoreRequest
import com.convergencelabs.server.datastore.ModelStoreActor
import com.convergencelabs.server.datastore.ApiKeyStoreActor.ApiKeyStoreRequest
import com.convergencelabs.server.datastore.ApiKeyStoreActor

object RestDomainActor {
  def props(domainFqn: DomainFqn): Props = Props(new RestDomainActor(domainFqn))

  case class DomainMessage(domainFqn: DomainFqn, message: Any)
  case object Shutdown

  val GracefulStopWaitTime: FiniteDuration = new FiniteDuration(30, TimeUnit.SECONDS)
}

class RestDomainActor(domainFqn: DomainFqn) extends Actor with ActorLogging {

  private[this] implicit val ec = context.dispatcher
  private[this] var userStoreActor: ActorRef = _
  private[this] var collectionStoreActor: ActorRef = _
  private[this] var modelStoreActor: ActorRef = _
  private[this] var keyStoreActor: ActorRef = _

  def receive: Receive = {
    case message: UserStoreRequest =>
      userStoreActor forward message
    case message: CollectionStoreRequest =>
      collectionStoreActor forward message
    case message: ModelStoreRequest =>
      modelStoreActor forward message
    case message: ApiKeyStoreRequest =>
      keyStoreActor forward message
    case Shutdown =>
      shutdown()
    case message: Any =>
      unhandled(message)
  }

  def shutdown(): Unit = {
    gracefulStop(userStoreActor, GracefulStopWaitTime, PoisonPill).onComplete(_ => context.stop(self))
    // FIXME shut all of these down?
  }

  override def preStart(): Unit = {
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) match {
      case Success(provider) =>
        userStoreActor = context.actorOf(UserStoreActor.props(provider.userStore))
        collectionStoreActor = context.actorOf(CollectionStoreActor.props(provider.collectionStore))
        modelStoreActor = context.actorOf(ModelStoreActor.props(provider.modelStore))
        keyStoreActor = context.actorOf(ApiKeyStoreActor.props(provider.keyStore))
      case Failure(cause) =>
        log.error(cause, "Unable to obtain a domain persistence provider.")
    }
  }

  override def postStop(): Unit = {
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }
}
