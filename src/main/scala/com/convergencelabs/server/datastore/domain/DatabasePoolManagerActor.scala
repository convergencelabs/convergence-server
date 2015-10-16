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
    domainConfigStore: DomainConfigurationStore): Props = Props(
    new DomainPersistenceManagerActor(domainConfigStore))

  def getLocalInstancePath(requestor: ActorPath): ActorPath = {
    requestor.root / "user" / RelativePath
  }

  def getPersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainFqn): DomainPersistenceProvider = {
    val path = DomainPersistenceManagerActor.getLocalInstancePath(requestor.path)
    val selection = context.actorSelection(path)

    val message = AcquireDomainPersistence(domainFqn)
    val timeout = Timeout(2, TimeUnit.SECONDS)
    val f = Patterns.ask(selection, message, timeout).mapTo[DomainPersistenceResponse]
    val result = Await.result(f, FiniteDuration(2, TimeUnit.SECONDS))

    result match {
      case PersistenceProviderReference(persistenceProvider) => persistenceProvider
      case PersistenceProviderUnavailable                    => throw new RuntimeException()
    }
  }
}

class DomainPersistenceManagerActor(domainConfigStore: DomainConfigurationStore) extends Actor with ActorLogging {

  private[this] var refernceCounts = Map[DomainFqn, Int]()
  private[this] var providers = Map[DomainFqn, DomainPersistenceProvider]()
  private[this] var providersByActor = Map[ActorRef, List[DomainFqn]]()

  def receive = {
    case AcquireDomainPersistence(domainFqn) => onAcquire(domainFqn)
    case ReleaseDomainPersistence(domainFqn) => onRelease(domainFqn)
    case Terminated(actor)                   => onActorDeath(actor)
  }

  private[this] def onAcquire(domainFqn: DomainFqn): Unit = {
    val p = providers.get(domainFqn) match {
      case Some(provider) => Success(provider)
      case None           => createProvider(domainFqn)
    }

    p match {
      case Success(provider) => {
        val newCount = refernceCounts.getOrElse(domainFqn, 0) + 1
        refernceCounts = refernceCounts + (domainFqn -> newCount)

        val newProviders = providersByActor.getOrElse(sender, List()) :+ domainFqn
        providersByActor = providersByActor + (sender -> newProviders)

        if (newProviders.length == 1) {
          // First time registered.  Watch this actor.
          context.watch(sender)
        }

        sender ! PersistenceProviderReference(provider)
      }
      case Failure(cause) => {
        sender ! PersistenceProviderUnavailable
      }
    }
  }

  private[this] def onRelease(domainFqn: DomainFqn): Unit = {
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
    val acquiredProviders = providersByActor.get(sender)
    if (acquiredProviders.isDefined) {

      acquiredProviders.get foreach (domainFqn => {
        decrementCount(domainFqn)
      })

      providersByActor = providersByActor - sender
      context.unwatch(sender)
    }
  }

  private[this] def decrementCount(domainFqn: DomainFqn): Unit = {
    val currentCount = refernceCounts.get(domainFqn)
    if (currentCount.isDefined) {
      val newCount = currentCount.get - 1

      if (newCount == 0) {
        shutdownPool(domainFqn)
      } else {
        // decrement
        refernceCounts = refernceCounts + (domainFqn -> newCount)
      }
    }
  }

  private[this] def createProvider(domainFqn: DomainFqn): Try[DomainPersistenceProvider] = Try({
    val config = domainConfigStore.getDomainConfig(domainFqn)
    config match {
      //TODO:  Need Uri
      case Some(domainConfig) => 
        new DomainPersistenceProvider(new OPartitionedDatabasePool("uri", domainConfig.dbUsername, domainConfig.dbPassword))
      case None => ??? // FIXME actually throw an exception here. 
    }
  })

  private[this] def shutdownPool(domainFqn: DomainFqn): Unit = {
    providers.get(domainFqn) match {
      case Some(provider) => {
        providers = providers - domainFqn
        provider.dbPool.close();
      }
      case None => {
        log.warning("Attempted to shutdown a persistence provider that was not open.")
      }
    }
  }
}

case class AcquireDomainPersistence(domainFqn: DomainFqn)
case class ReleaseDomainPersistence(domainFqn: DomainFqn)

sealed trait DomainPersistenceResponse
case class PersistenceProviderReference(persistenceProvider: DomainPersistenceProvider) extends DomainPersistenceResponse
case object PersistenceProviderUnavailable extends DomainPersistenceResponse