package com.convergencelabs.server.datastore.domain

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.convergence.DomainStore
import com.convergencelabs.server.db.PooledDatabaseProvider
import com.convergencelabs.server.db.provision.DomainProvisionerActor.DomainDeleted
import com.convergencelabs.server.db.provision.DomainProvisionerActor.DomainLifecycleTopic
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorLogging
import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.pattern.Patterns
import akka.util.Timeout
import grizzled.slf4j.Logging

trait DomainPersistenceManager {
  def acquirePersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainFqn): Try[DomainPersistenceProvider]
  def releasePersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainFqn): Unit
}

object DomainPersistenceManagerActor extends DomainPersistenceManager with Logging {
  val RelativePath = "DomainPersistenceManagerActor"
  val persistenceProviderTimeout = 5

  def props(
    baseDbUri: String,
    domainStore: DomainStore): Props = Props(
    new DomainPersistenceManagerActor(baseDbUri, domainStore))

  def getLocalInstancePath(requestor: ActorPath): ActorPath = {
    requestor.root / "user" / RelativePath
  }

  def acquirePersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainFqn): Try[DomainPersistenceProvider] = {
    val path = DomainPersistenceManagerActor.getLocalInstancePath(requestor.path)
    val selection = context.actorSelection(path)

    val message = AcquireDomainPersistence(domainFqn, requestor)
    val timeout = Timeout(persistenceProviderTimeout, TimeUnit.SECONDS)
    debug(s"Sending message to aquire domain persistence for ${domainFqn} by ${requestor.path}")
    Try {
      val f = Patterns.ask(selection, message, timeout).mapTo[DomainPersistenceProvider]
      Await.result(f, FiniteDuration(persistenceProviderTimeout, TimeUnit.SECONDS))
    }
  }

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
  private[this] var providers = Map[DomainFqn, DomainPersistenceProviderImpl]()
  private[this] var providersByActor = Map[ActorRef, List[DomainFqn]]()

  val mediator = DistributedPubSub(context.system).mediator

  // TODO we could specifically subscribe to a topic when it is acquired
  // if we find that to many messages are going all over the place.
  mediator ! Subscribe(DomainLifecycleTopic, self)

  override def receive: Receive = {
    case AcquireDomainPersistence(domainFqn, requestor) =>
      onAcquire(domainFqn, requestor)
    case ReleaseDomainPersistence(domainFqn) =>
      onRelease(domainFqn)
    case DomainDeleted(domainFqn) =>
      this.onDomainDeleted(domainFqn)
    case Terminated(actor) =>
      onActorDeath(actor)
  }

  private[this] def onAcquire(domainFqn: DomainFqn, requestor: ActorRef): Unit = {
    log.debug(s"${domainFqn}: Acquiring domain persistence for ${requestor.path}")
    providers.get(domainFqn)
      .map(Success(_))
      .getOrElse(createProvider(domainFqn))
      .map { provider =>
        val newCount = refernceCounts.getOrElse(domainFqn, 0) + 1
        refernceCounts = refernceCounts + (domainFqn -> newCount)

        val newProviders = providersByActor.getOrElse(sender, List()) :+ domainFqn
        providersByActor = providersByActor + (requestor -> newProviders)

        if (newProviders.length == 1) {
          // First time registered.  Watch this actor.
          context.watch(requestor)
        }

        sender ! provider
      } recover {
        case cause: DomainNotFoundException =>
          sender ! Status.Failure(cause)
        case cause: Throwable => {
          log.error(cause, s"${domainFqn}: Unable obtain a persistence provider")
          sender ! Status.Failure(cause)
        }
      }
  }

  private[this] def onRelease(domainFqn: DomainFqn): Unit = {
    log.debug(s"${domainFqn}: Releasing domain persistence for ${sender.path}")
    decrementCount(domainFqn)

    providersByActor.get(sender) map { pools =>
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
    log.debug(s"Unregistering all persistence providers for died actor: ${actor.path}")
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

  private[this] def createProvider(domainFqn: DomainFqn): Try[DomainPersistenceProvider] = {
    log.debug(s"${domainFqn}: Creating new persistence provider")
    domainStore.getDomainDatabase(domainFqn) flatMap {
      case Some(domainInfo) =>

        log.debug(s"${domainFqn}: Creating new connection pool: ${baseDbUri}/${domainInfo.database}")

        // FIXME need to figure out how to configure pool sizes.
        val dbProvider = new PooledDatabaseProvider(baseDbUri, domainInfo.database, domainInfo.username, domainInfo.password)
        val provider = new DomainPersistenceProviderImpl(dbProvider)
        dbProvider.connect()
          .flatMap(_ => provider.validateConnection())
          .flatMap { _ =>
            log.debug(s"Successfully created connection pool for '${domainFqn}':  ${baseDbUri}/${domainInfo.database}")
            providers = providers + (domainFqn -> provider)
            Success(provider)
          }
      case None =>
        log.debug(s"${domainFqn}: Requested to look up a domain that does not exist.")
        Failure(DomainNotFoundException(domainFqn))
    }
  }

  private[this] def onDomainDeleted(domainFqn: DomainFqn): Unit = {
    if (providers.contains(domainFqn)) {
      log.debug(s"${domainFqn}: Domain deleted, shutting down connection pool")
      shutdownPool(domainFqn)
    }
  }

  private[this] def shutdownPool(domainFqn: DomainFqn): Unit = {
    providers.get(domainFqn) match {
      case Some(provider) => {
        log.debug(s"${domainFqn}: Shutting down persistence provider")
        providers = providers - domainFqn
        refernceCounts = refernceCounts - domainFqn
        provider.shutdown()
      }
      case None => {
        log.warning(s"${domainFqn}: Attempted to shutdown a persistence provider that was not open")
      }
    }
  }
}

case class DomainNotFoundException(domainFqn: DomainFqn) extends Exception(s"The requested domain does not exist: ${domainFqn}")

case class AcquireDomainPersistence(domainFqn: DomainFqn, requestor: ActorRef)
case class ReleaseDomainPersistence(domainFqn: DomainFqn)
