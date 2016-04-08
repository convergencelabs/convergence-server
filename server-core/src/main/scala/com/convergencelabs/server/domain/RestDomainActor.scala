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
import scala.concurrent.duration.FiniteDuration
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

object RestDomainActor {
  def props(domainFqn: DomainFqn): Props = Props(new RestDomainActor(domainFqn))

  trait DomainMessage {
    def domainFqn: DomainFqn
  }
}

class RestDomainActor(domainFqn: DomainFqn) extends Actor with ActorLogging {

  private[this] var persistenceProvider: DomainPersistenceProvider = _
  private[this] implicit val ec = context.dispatcher

  def receive: Receive = {
    case message: Any => unhandled(message)
  }

  override def preStart(): Unit = {
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) match {
      case Success(provider) => persistenceProvider = provider
      case Failure(cause) => {
        log.error(cause, "Unable to obtain a domain persistence provider.")
      }
    }
  }

  override def postStop(): Unit = {
    log.debug(s"Domain(${domainFqn}) received shutdown command.  Shutting down.")
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }
}
