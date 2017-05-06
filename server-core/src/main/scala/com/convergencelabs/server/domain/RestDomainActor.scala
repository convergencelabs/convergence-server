package com.convergencelabs.server.domain

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.common.ConvergenceJwtUtil
import com.convergencelabs.server.datastore.JwtAuthKeyStoreActor
import com.convergencelabs.server.datastore.JwtAuthKeyStoreActor.ApiKeyStoreRequest
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
import com.convergencelabs.server.datastore.ConfigStoreActor.ConfigStoreRequest
import com.convergencelabs.server.datastore.ConfigStoreActor
import com.convergencelabs.server.domain.stats.DomainStatsActor.DomainStatsRequest
import com.convergencelabs.server.domain.stats.DomainStatsActor
import com.convergencelabs.server.datastore.SessionStoreActor.SessionStoreRequest
import com.convergencelabs.server.datastore.SessionStoreActor
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.ModelPermissionsStoreRequest
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor
import com.convergencelabs.server.datastore.UserGroupStoreActor.UserGroupStoreRequest
import com.convergencelabs.server.datastore.UserGroupStoreActor

object RestDomainActor {
  def props(domainFqn: DomainFqn): Props = Props(new RestDomainActor(domainFqn))

  case object Shutdown
  case class AdminTokenRequest(convergenceUsername: String)
}

class RestDomainActor(domainFqn: DomainFqn) extends Actor with ActorLogging {

  private[this] implicit val ec = context.dispatcher
  private[this] var userStoreActor: ActorRef = _
  private[this] var statsActor: ActorRef = _
  private[this] var collectionStoreActor: ActorRef = _
  private[this] var modelStoreActor: ActorRef = _
  private[this] var modelPermissionsStoreActor: ActorRef = _
  private[this] var keyStoreActor: ActorRef = _
  private[this] var sessionStoreActor: ActorRef = _
  private[this] var configStoreActor: ActorRef = _
  private[this] var groupStoreActor: ActorRef = _
  private[this] var domainConfigStore: DomainConfigStore = _

  val MaxShutdownWaitTime = Duration.fromNanos(
    context.system.settings.config.getDuration("convergence.rest.max-rest-actor-shutdown").toNanos())

  def receive: Receive = {
    case AdminTokenRequest(convergenceUsername) =>
      getAdminToken(convergenceUsername)
    case message: UserStoreRequest =>
      userStoreActor forward message
    case message: UserGroupStoreRequest =>
      groupStoreActor forward message
    case message: CollectionStoreRequest =>
      collectionStoreActor forward message
    case message: ModelStoreRequest =>
      modelStoreActor forward message
    case message: ModelPermissionsStoreRequest =>
      modelPermissionsStoreActor forward message
    case message: ApiKeyStoreRequest =>
      keyStoreActor forward message
    case message: ConfigStoreRequest =>
      configStoreActor forward message
    case message: DomainStatsRequest =>
      statsActor forward message
    case message: SessionStoreRequest =>
      sessionStoreActor forward message
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

  def getAdminToken(convergenceUsername: String): Unit = {
    domainConfigStore.getAdminKeyPair() flatMap { pair =>
      ConvergenceJwtUtil.fromString(AuthenticationHandler.AdminKeyId, pair.privateKey)
    } flatMap { util =>
      util.generateToken(convergenceUsername)
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

        statsActor = context.actorOf(DomainStatsActor.props(provider))
        userStoreActor = context.actorOf(UserStoreActor.props(provider.userStore))
        configStoreActor = context.actorOf(ConfigStoreActor.props(provider.configStore))
        collectionStoreActor = context.actorOf(CollectionStoreActor.props(provider.collectionStore))
        modelStoreActor = context.actorOf(ModelStoreActor.props(provider))
        modelPermissionsStoreActor = context.actorOf(ModelPermissionsStoreActor.props(provider.modelPermissionsStore))
        keyStoreActor = context.actorOf(JwtAuthKeyStoreActor.props(provider.jwtAuthKeyStore))
        sessionStoreActor = context.actorOf(SessionStoreActor.props(provider.sessionStore))
        groupStoreActor = context.actorOf(UserGroupStoreActor.props(provider.userGroupStore))

      case Failure(cause) =>
        log.error(cause, "Unable to obtain a domain persistence provider.")
    }
  }

  override def postStop(): Unit = {
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }
}
