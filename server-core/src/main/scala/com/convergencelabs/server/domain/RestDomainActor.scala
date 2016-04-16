package com.convergencelabs.server.domain

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.common.ConvergenceJwtUtil
import com.convergencelabs.server.datastore.ApiKeyStoreActor
import com.convergencelabs.server.datastore.ApiKeyStoreActor.ApiKeyStoreRequest
import com.convergencelabs.server.datastore.CollectionStoreActor
import com.convergencelabs.server.datastore.CollectionStoreActor.CollectionStoreRequest
import com.convergencelabs.server.datastore.ModelStoreActor
import com.convergencelabs.server.datastore.ModelStoreActor.ModelStoreRequest
import com.convergencelabs.server.datastore.UserStoreActor
import com.convergencelabs.server.datastore.UserStoreActor.UserStoreRequest
import com.convergencelabs.server.datastore.domain.DomainConfigStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.RestDomainActor.AdminTokenRequest
import com.convergencelabs.server.domain.RestDomainActor.Shutdown

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.gracefulStop
import scala.concurrent.Future
import scala.concurrent.duration.Duration

object RestDomainActor {
  def props(domainFqn: DomainFqn): Props = Props(new RestDomainActor(domainFqn))

  case object Shutdown
  case class AdminTokenRequest(convergenceUserId: String)
}

class RestDomainActor(domainFqn: DomainFqn) extends Actor with ActorLogging {

  private[this] implicit val ec = context.dispatcher
  private[this] var userStoreActor: ActorRef = _
  private[this] var collectionStoreActor: ActorRef = _
  private[this] var modelStoreActor: ActorRef = _
  private[this] var keyStoreActor: ActorRef = _
  private[this] var domainConfigStore: DomainConfigStore = _

  val MaxShutdownWaitTime = Duration.fromNanos(
    context.system.settings.config.getDuration("convergence.rest.max-rest-actor-shutdown").toNanos())

  def receive: Receive = {
    case AdminTokenRequest(convergenceUserId) =>
      getAdminToken(convergenceUserId)
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

  def receiveWhileShuttingDown: Receive = {
    case message: Any =>
      log.warning("receveid message while shuttng down.")
      unhandled(message)
  }

  def getAdminToken(convergenceUserId: String): Unit = {
    domainConfigStore.getAdminKeyPair() flatMap { pair =>
      // FIXME hardcoded
      ConvergenceJwtUtil.fromString("ConvergenceAdminKey", pair.privateKey)
    } flatMap { util =>
      val username = domainConfigStore.getAdminUserName()
      util.generateToken(username)
    } match {
      case Success(token) => sender ! token
      case Failure(cause) => sender ! akka.actor.Status.Failure(cause)
    }
  }

  def shutdown(): Unit = {
    context.become(receiveWhileShuttingDown)
    Future.sequence(List(
      gracefulStop(userStoreActor, MaxShutdownWaitTime, PoisonPill),
      gracefulStop(collectionStoreActor, MaxShutdownWaitTime, PoisonPill),
      gracefulStop(modelStoreActor, MaxShutdownWaitTime, PoisonPill),
      gracefulStop(keyStoreActor, MaxShutdownWaitTime, PoisonPill)))
      .onComplete(_ => context.stop(self))
  }

  override def preStart(): Unit = {
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) match {
      case Success(provider) =>
        domainConfigStore = provider.configStore

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
