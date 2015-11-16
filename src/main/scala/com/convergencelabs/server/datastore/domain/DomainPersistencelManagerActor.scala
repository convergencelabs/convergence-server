package com.convergencelabs.server.datastore.domain

import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.DomainFqn
import akka.actor.Terminated
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import akka.actor.actorRef2Scala
import com.convergencelabs.server.datastore.DomainConfigurationStore
import akka.actor.ActorPath
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import akka.pattern.Patterns
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import akka.actor.ActorContext

object DomainPersistenceManagerActor {
  val RelativePath = "DomainPersistenceManagerActor"

  def props(
    baseDbUri: String,
    domainConfigStore: DomainConfigurationStore): Props = Props(
    new DomainPersistenceManagerActor(baseDbUri, domainConfigStore))

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
    domainConfigStore: DomainConfigurationStore) extends Actor with ActorLogging {

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
        sender ! PersistenceProviderUnavailable(cause)
      }
    }
  }

  private[this] def onRelease(domainFqn: DomainFqn): Unit = {
    log.debug(s"Releasing domain persistence: ${domainFqn}")
    decrementCount(domainFqn)

    val acquiredProviders = providersByActor.get(sender)
    if (acquiredProviders.isDefined) {
      val pools = acquiredProviders.get
      val newPools = pools diff List(domainFqn)

      if (newPools.length == 0) {
        providersByActor = providersByActor - sender
        // This actor no longer has any connections open.
        context.unwatch(sender)
      } else {
        providersByActor = providersByActor + (sender -> newPools)
      }
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
    log.warning(s"Creating new persistence provider: ${domainFqn}")
    val config = domainConfigStore.getDomainConfigByFqn(domainFqn)
    config match {
      case Some(domainConfig) => {
        val pool = new OPartitionedDatabasePool(
          baseDbUri + "/" + domainConfig.id,
          domainConfig.dbUsername,
          domainConfig.dbPassword)
        val provider = new DomainPersistenceProvider(pool)
        providers = providers + (domainFqn -> provider)
        provider
      }
      case None => ??? // FIXME actually throw an exception here. 
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