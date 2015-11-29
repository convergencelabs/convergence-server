package com.convergencelabs.server.datastore.domain

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorLogging
import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import akka.pattern.Patterns
import akka.util.Timeout

object DomainPersistenceManagerActor {
  val RelativePath = "DomainPersistenceManagerActor"

  def props(
    baseDbUri: String,
    domainStore: DomainStore): Props = Props(
    new DomainPersistenceManagerActor(baseDbUri, domainStore))

  def getLocalInstancePath(requestor: ActorPath): ActorPath = {
    requestor.root / "user" / RelativePath
  }

  def acquirePersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainFqn): Try[DomainPersistenceProvider] = Try({
    val path = DomainPersistenceManagerActor.getLocalInstancePath(requestor.path)
    val selection = context.actorSelection(path)

    val message = AcquireDomainPersistence(domainFqn, requestor)
    val timeout = Timeout(2, TimeUnit.SECONDS)
    val f = Patterns.ask(selection, message, timeout).mapTo[DomainPersistenceResponse]
    val result = Await.result(f, FiniteDuration(2, TimeUnit.SECONDS))

    result match {
      case PersistenceProviderReference(persistenceProvider) => persistenceProvider
      case PersistenceProviderUnavailable(cause) => throw cause
    }
  })
  
  def releasePersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainFqn): Unit = {
    val path = DomainPersistenceManagerActor.getLocalInstancePath(requestor.path)
    val selection = context.actorSelection(path)
    selection.tell(ReleaseDomainPersistence(domainFqn), requestor)
  }
}

class DomainPersistenceManagerActor(
    baseDbUri: String,
    domainStore: DomainStore) extends Actor with ActorLogging {

  private[this] var refernceCounts = Map[DomainFqn, Int]()
  private[this] var providers = Map[DomainFqn, DomainPersistenceProvider]()
  private[this] var providersByActor = Map[ActorRef, List[DomainFqn]]()

  def receive = {
    case AcquireDomainPersistence(domainFqn, requestor) => onAcquire(domainFqn, requestor)
    case ReleaseDomainPersistence(domainFqn) => onRelease(domainFqn)
    case Terminated(actor) => onActorDeath(actor)
  }

  private[this] def onAcquire(domainFqn: DomainFqn, requestor: ActorRef): Unit = {
    log.debug(s"Acquiring domain persistence for ${domainFqn} by ${requestor.path}")
    val p = providers.get(domainFqn) match {
      case Some(provider) => Success(provider)
      case None => createProvider(domainFqn)
    }

    p match {
      case Success(provider) => {
        val newCount = refernceCounts.getOrElse(domainFqn, 0) + 1
        refernceCounts = refernceCounts + (domainFqn -> newCount)

        val newProviders = providersByActor.getOrElse(sender, List()) :+ domainFqn
        providersByActor = providersByActor + (requestor -> newProviders)

        if (newProviders.length == 1) {
          // First time registered.  Watch this actor.
          context.watch(requestor)
        }

        sender ! PersistenceProviderReference(provider)
      }
      case Failure(cause) => {
        log.debug("Unable obtain a persistence provider")
        sender ! PersistenceProviderUnavailable(cause)
      }
    }
  }

  private[this] def onRelease(domainFqn: DomainFqn): Unit = {
    log.debug(s"Releasing domain persistence: ${domainFqn}")
    decrementCount(domainFqn)

    providersByActor.get(sender) match {
      case Some(pools) => 
        val newPools = pools diff List(domainFqn)
        if (newPools.length == 0) {
          providersByActor = providersByActor - sender
          // This actor no longer has any connections open.
          context.unwatch(sender)
        } else {
          providersByActor = providersByActor + (sender -> newPools)
        }
      case None =>
    }
  }

  private[this] def onActorDeath(actor: ActorRef): Unit = {
    log.debug(s"Unregistering persistence providers for died actor: ${actor.path}")
    providersByActor.get(actor) foreach (acquiredProviders => {
      acquiredProviders foreach (domainFqn => {
        decrementCount(domainFqn)
      })

      providersByActor = providersByActor - sender
      context.unwatch(actor)
    })
  }

  private[this] def decrementCount(domainFqn: DomainFqn): Unit = {
    refernceCounts.get(domainFqn) foreach (currentCount => {
      val newCount = currentCount - 1
      if (newCount == 0) {
        shutdownPool(domainFqn)
      } else {
        // decrement
        refernceCounts = refernceCounts + (domainFqn -> newCount)
      }
    })
  }

  private[this] def createProvider(domainFqn: DomainFqn): Try[DomainPersistenceProvider] = Try({
    log.debug(s"Creating new persistence provider: ${domainFqn}")
    domainStore.getDomainByFqn(domainFqn) match {
      case Success(Some(domainInfo)) => {
        val pool = new OPartitionedDatabasePool(
          baseDbUri + domainInfo.id,
          domainInfo.dbUsername,
          domainInfo.dbPassword)
        log.debug(s"Creating new connection pool for '${domainFqn}': ${pool.getUrl}")
        val provider = new DomainPersistenceProvider(pool)
        provider.validateConnection() match {
          case false => throw new IllegalStateException("unable to connect to the database")
          case true =>
        }
        providers = providers + (domainFqn -> provider)
        provider
      }
      case Success(None) => {
        throw new IllegalStateException(
            s"Error looking up the domain record for $domainFqn, when initializing a domain persistence provider.")
      }
      case Failure(cause) => {
        log.debug(cause.getMessage)
        throw cause
      }
    }
  })

  private[this] def shutdownPool(domainFqn: DomainFqn): Unit = {
    providers.get(domainFqn) match {
      case Some(provider) => {
        log.warning(s"Shutting down persistence provider: ${domainFqn}")
        providers = providers - domainFqn
        refernceCounts = refernceCounts - domainFqn
        provider.dbPool.close()
      }
      case None => {
        log.warning(s"Attempted to shutdown a persistence provider that was not open: ${domainFqn}")
      }
    }
  }
}

case class AcquireDomainPersistence(domainFqn: DomainFqn, requestor: ActorRef)
case class ReleaseDomainPersistence(domainFqn: DomainFqn)

sealed trait DomainPersistenceResponse
case class PersistenceProviderReference(persistenceProvider: DomainPersistenceProvider) extends DomainPersistenceResponse
case class PersistenceProviderUnavailable(cuase: Throwable) extends DomainPersistenceResponse