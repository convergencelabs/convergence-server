package com.convergencelabs.server.domain.rest

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

import com.convergencelabs.common.ConvergenceJwtUtil
import com.convergencelabs.server.actor.ShardedActor
import com.convergencelabs.server.actor.ShardedActorStatUpPlan
import com.convergencelabs.server.actor.StartUpRequired
import com.convergencelabs.server.datastore.domain.CollectionStoreActor
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.CollectionStoreRequest
import com.convergencelabs.server.datastore.domain.ConfigStoreActor
import com.convergencelabs.server.datastore.domain.ConfigStoreActor.ConfigStoreRequest
import com.convergencelabs.server.datastore.domain.DomainConfigStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.server.datastore.domain.DomainStatsActor
import com.convergencelabs.server.datastore.domain.DomainStatsActor.DomainStatsRequest
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStoreActor
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStoreActor.ApiKeyStoreRequest
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.ModelPermissionsStoreRequest
import com.convergencelabs.server.datastore.domain.ModelStoreActor
import com.convergencelabs.server.datastore.domain.ModelStoreActor.ModelStoreRequest
import com.convergencelabs.server.datastore.domain.SessionStoreActor
import com.convergencelabs.server.datastore.domain.SessionStoreActor.SessionStoreRequest
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor.UserGroupStoreRequest
import com.convergencelabs.server.datastore.domain.UserStoreActor
import com.convergencelabs.server.datastore.domain.UserStoreActor.UserStoreRequest
import com.convergencelabs.server.domain.AuthenticationHandler
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage

import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.actor.actorRef2Scala

object RestDomainActor {
  def props(
    domainPersistenceManager: DomainPersistenceManager,
    receiveTimeout: FiniteDuration): Props = Props(new RestDomainActor(domainPersistenceManager, receiveTimeout))

  case class AdminTokenRequest(convergenceUsername: String)
  case class DomainRestMessage(domainFqn: DomainFqn, message: Any)
}

class RestDomainActor(domainPersistenceManager: DomainPersistenceManager, receiveTimeout: FiniteDuration)
  extends ShardedActor[DomainRestMessage](classOf[DomainRestMessage]) {

  import RestDomainActor._

  private[this] var domainFqn: DomainFqn = _
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

  this.context.setReceiveTimeout(this.receiveTimeout)

  val MaxShutdownWaitTime = Duration.fromNanos(
    context.system.settings.config.getDuration("convergence.rest.max-rest-actor-shutdown").toNanos())

  def receiveInitialized: Receive = {
    case DomainRestMessage(fqn, msg) =>
      receiveDomainRestMessage(msg)
    case ReceiveTimeout =>
      passivate()
    case message: Any =>
      unhandled(message)
  }

  def receiveDomainRestMessage(msg: Any): Unit = {
    msg match {
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
      case message: Any =>
        unhandled(message)
    }
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

  override protected def initialize(msg: DomainRestMessage): Try[ShardedActorStatUpPlan] = {
    log.debug(s"DomainActor initializing: '{}'", msg.domainFqn)
    domainPersistenceManager.acquirePersistenceProvider(self, context, msg.domainFqn) map { provider =>
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

      log.debug(s"RestDomainActor initialized: {}", domainFqn)
      StartUpRequired
    } recoverWith {
      case NonFatal(cause) =>
        log.debug(s"Error initializing DomainActor: {}", domainFqn)
        Failure(cause)
    }
  }

  override protected def passivate(): Unit = {
    super.passivate()
    Option(this.domainFqn).map { d =>
      domainPersistenceManager.releasePersistenceProvider(self, context, d)
    }
  }
  
  override protected def setIdentityData(message: DomainRestMessage): Try[String] = {
    this.domainFqn = message.domainFqn
    Success(s"${message.domainFqn.namespace}/${message.domainFqn.domainId}")
  }
}
