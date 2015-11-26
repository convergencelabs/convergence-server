package com.convergencelabs.server.domain

import scala.collection.mutable
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Cancellable
import com.convergencelabs.server.domain.model.ModelManagerActor
import com.convergencelabs.server.ProtocolConfiguration
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.jwt.consumer.InvalidJwtException
import java.io.IOException
import org.jose4j.jwt.MalformedClaimException
import scala.util.control.NonFatal
import scala.collection.mutable.ListBuffer
import org.jose4j.jwt.JwtClaims
import java.security.PublicKey
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.ConfigurationStore
import java.util.UUID
import scala.util.Success
import scala.util.Failure
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import akka.pattern.Patterns
import com.convergencelabs.server.datastore.domain.AcquireDomainPersistence
import akka.util.Timeout
import scala.concurrent.Await
import com.convergencelabs.server.datastore.domain.DomainPersistenceResponse
import com.convergencelabs.server.datastore.domain.PersistenceProviderReference
import com.convergencelabs.server.datastore.domain.PersistenceProviderUnavailable
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import scala.util.Try

// FIXME protocol config is silly.
object DomainActor {
  def props(
    domainManagerActor: ActorRef,
    domainFqn: DomainFqn,
    protocolConfig: ProtocolConfiguration,
    shutdownDelay: FiniteDuration): Props = Props(
    new DomainActor(
      domainManagerActor,
      domainFqn,
      protocolConfig))

  private val MaxSessionId = 2176782335L
}

/**
 * The [[com.convergencelabs.server.domain.DomainActor]] is the supervisor for
 * all actor that comprise the services provided by a particular domain.
 */
class DomainActor(
  domainManagerActor: ActorRef,
  domainFqn: DomainFqn,
  protocolConfig: ProtocolConfiguration)
    extends Actor
    with ActorLogging {

  log.debug(s"Domain startting up: ${domainFqn}")

  private[this] var persistenceProvider: DomainPersistenceProvider = _
  private[this] implicit val ec = context.dispatcher
  private[this] var nextSessionId = 0L

  private[this] val modelManagerActorRef = context.actorOf(ModelManagerActor.props(
    domainFqn,
    protocolConfig),
    ModelManagerActor.RelativePath)

  private[this] var authenticator: AuthenticationHandler = null

  log.debug(s"Domain start up complete: ${domainFqn}")

  private[this] val connectedClients = mutable.Set[ActorRef]()

  def receive = {
    case message: HandshakeRequest => onHandshakeRequest(message)
    case message: AuthenticationRequest => onAuthenticationRequest(message)
    case message: ClientDisconnected => onClientDisconnect(message)
    case message => unhandled(message)
  }

  private[this] def onAuthenticationRequest(message: AuthenticationRequest): Unit = {
    val asker = sender
    authenticator.authenticate(message) onComplete {
      case Success(x) => asker ! x
      case Failure(e) => {
        asker ! AuthenticationFailure
      }
    }
  }

  private[this] def onHandshakeRequest(message: HandshakeRequest): Unit = {
    persistenceProvider.validateConnection() match {
      case true => {
        connectedClients += message.clientActor
        val (sessionId, reconnectToken) = message.reconnect match {
          case false => {
            (generateNextSessionId(), generateSessionToken())
          }
          case true => {
            // FIXME Are we doing anything with reconnection?
            ("todo", "todo")
          }
        }
        sender ! HandshakeSuccess(sessionId, reconnectToken, self, modelManagerActorRef)
      }
      case false => {
        sender ! HandshakeFailure("domain_unavailable", "Could not connect to database.")
      }
    }
  }

  private[this] def onClientDisconnect(message: ClientDisconnected): Unit = {
    log.debug(s"Client disconnecting: ${message.sessionId}")

    connectedClients.remove(sender())
    if (connectedClients.isEmpty) {
      log.debug(s"Last client disconnected from domain: ${domainFqn}")

      domainManagerActor ! DomainShutdownRequest(domainFqn)
    }
  }

  def generateNextSessionId(): String = {
    val sessionId = nextSessionId

    if (nextSessionId < DomainActor.MaxSessionId) {
      nextSessionId += 1
    } else {
      nextSessionId = 0
    }

    java.lang.Long.toString(sessionId, 36)
  }

  def generateSessionToken(): String = {
    UUID.randomUUID().toString() + UUID.randomUUID().toString()
  }

  override def preStart(): Unit = {
    val p = DomainPersistenceManagerActor.acquirePersistenceProvider(
      self, context, domainFqn)

    p match {
      case Success(provider) => {
        this.persistenceProvider = provider
        authenticator = new AuthenticationHandler(
          provider.configStore,
          provider.userStore,
          context.dispatcher)
      }
      case Failure(cause) => {
        log.error(cause, "foo")
      }
    }
  }
  
  override def postStop(): Unit = {
    log.debug(s"Domain(${domainFqn}) received shutdown command.  Shutting down.")
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }
}