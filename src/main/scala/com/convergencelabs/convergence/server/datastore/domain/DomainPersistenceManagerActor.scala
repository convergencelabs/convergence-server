/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.datastore.domain

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorContext, ActorLogging, ActorPath, ActorRef, Props, Status, Terminated, actorRef2Scala}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.pattern.Patterns
import akka.util.Timeout
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.convergence.DomainStore
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceManagerActor.{AcquireDomainPersistence, DomainNotFoundException, ReleaseDomainPersistence}
import com.convergencelabs.convergence.server.db.PooledDatabaseProvider
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor.{DomainDeleted, DomainLifecycleTopic}
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import grizzled.slf4j.Logging

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}


/**
 * The [[DomainPersistenceManagerActor]] implements a reference counted
 * flyweight pattern for [[DomainPersistenceProvider]]s. When a consumer
 * requests a DomainPersistenceProvider, one will be created if it does
 * not exists. If another consumer requests the provider for the same
 * domain, the same instance will be returned. When a persistence provider
 * for a particular domain is acquired, a reference counter is increased.
 * When the consumer releases the domain, or when the consumer dies, the
 * reference counter is decreased. When no more consumer are using the
 * domain, the persistence provider will be shut down.
 *
 * @param baseDbUri The base uri of the database.
 * @param domainStore The domain store to look up domain databases with.
 */
class DomainPersistenceManagerActor(private[this] val baseDbUri: String,
                                    private[this] val domainStore: DomainStore)
  extends Actor with ActorLogging {

  private[this] var referenceCounts = Map[DomainId, Int]()
  private[this] var providers = Map[DomainId, DomainPersistenceProviderImpl]()
  private[this] var providersByActor = Map[ActorRef, List[DomainId]]()

  private[this] val mediator = DistributedPubSub(context.system).mediator

  // TODO we could specifically subscribe to a topic when it is acquired
  //   if we find that to many messages are going all over the place.
  mediator ! Subscribe(DomainLifecycleTopic, self)

  override def receive: Receive = {
    case AcquireDomainPersistence(domainId, requester) =>
      onAcquire(domainId, requester, sender)
    case ReleaseDomainPersistence(domainId, requester) =>
      onRelease(domainId, requester)
    case DomainDeleted(domainId) =>
      this.onDomainDeleted(domainId)
    case Terminated(actor) =>
      onActorDeath(actor)
    case x: Any =>
      unhandled(x)
  }

  private[this] def onAcquire(domainId: DomainId, requester: ActorRef, replyTo: ActorRef): Unit = {
    log.debug(s"$domainId: Acquiring domain persistence for ${requester.path}")
    providers.get(domainId)
      .map(Success(_))
      .getOrElse(createProvider(domainId))
      .map { provider =>
        val newCount = referenceCounts.getOrElse(domainId, 0) + 1
        referenceCounts += (domainId -> newCount)

        val newProviders = providersByActor.getOrElse(requester, List()) :+ domainId
        providersByActor += (requester -> newProviders)

        if (newProviders.length == 1) {
          // First time registered.  Watch this actor.
          context.watch(requester)
        }

        replyTo ! provider
      }
      .recover {
        case cause: DomainNotFoundException =>
          replyTo ! Status.Failure(cause)
        case cause: Throwable =>
          log.error(cause, s"$domainId: Unable obtain a persistence provider")
          replyTo ! Status.Failure(cause)
      }
  }

  private[this] def onRelease(domainId: DomainId, requester: ActorRef): Unit = {
    log.debug(s"$domainId: Releasing domain persistence for ${requester.path}")

    decrementDomainReferenceCount(domainId)

    providersByActor.get(requester) foreach { pools =>
      val newPools = pools diff List(domainId)
      if (newPools.isEmpty) {
        providersByActor = providersByActor - requester
        // This actor no longer has any databases open.
        context.unwatch(requester)
      } else {
        providersByActor = providersByActor + (requester -> newPools)
      }
    }
  }

  private[this] def onActorDeath(terminatedActor: ActorRef): Unit = {
    log.debug(s"Unregistering all persistence providers for died actor: ${terminatedActor.path}")
    context.unwatch(terminatedActor)
    providersByActor.get(terminatedActor) foreach (_.foreach(decrementDomainReferenceCount))
    providersByActor -= terminatedActor
  }

  private[this] def decrementDomainReferenceCount(domainId: DomainId): Unit = {
    referenceCounts.get(domainId) foreach (currentCount => {
      if (currentCount == 1) {
        // This was the last consumer. Shut it down.
        shutdownPool(domainId)
      } else {
        // otherwise decrement the count
        referenceCounts += (domainId -> (currentCount - 1))
      }
    })
  }

  private[this] def createProvider(domainId: DomainId): Try[DomainPersistenceProvider] = {
    log.debug(s"$domainId: Creating new persistence provider")
    domainStore.getDomainDatabase(domainId) flatMap {
      case Some(domainInfo) =>
        log.debug(s"$domainId: Creating new connection pool: $baseDbUri/${domainInfo.database}")

        // FIXME need to figure out how to configure pool sizes.
        val dbProvider = new PooledDatabaseProvider(baseDbUri, domainInfo.database, domainInfo.username, domainInfo.password)
        val provider = new DomainPersistenceProviderImpl(dbProvider)
        dbProvider.connect()
          .flatMap(_ => provider.validateConnection())
          .flatMap { _ =>
            log.debug(s"Successfully created connection pool for '$domainId':  $baseDbUri/${domainInfo.database}")
            providers += (domainId -> provider)
            Success(provider)
          }
      case None =>
        log.debug(s"$domainId: Requested to look up a domain that does not exist.")
        Failure(DomainNotFoundException(domainId))
    }
  }

  private[this] def onDomainDeleted(domainId: DomainId): Unit = {
    if (providers.contains(domainId)) {
      log.debug(s"$domainId: Domain deleted, shutting down connection pool")
      shutdownPool(domainId)
    }
  }

  private[this] def shutdownPool(domainId: DomainId): Unit = {
    providers.get(domainId) match {
      case Some(provider) =>
        log.debug(s"$domainId: Shutting down persistence provider")
        providers -= domainId
        referenceCounts -= domainId
        provider.shutdown()
      case None =>
        log.warning(s"$domainId: Attempted to shutdown a persistence provider that was not open")
    }
  }
}


/**
 * The companion object for the [[DomainPersistenceManagerActor]] class
 * provided helper methods to instantiate the [[DomainPersistenceManagerActor]]
 * and also implements the [[DomainPersistenceManager]] trait allowing
 * consumers to easily use the [[DomainPersistenceManagerActor]] as a
 * [[DomainPersistenceManager]].
 */
object DomainPersistenceManagerActor extends DomainPersistenceManager with Logging {
  val RelativePath = "DomainPersistenceManagerActor"
  val persistenceProviderTimeout = 10

  def props(baseDbUri: String,
            domainStore: DomainStore): Props = Props(
    new DomainPersistenceManagerActor(baseDbUri, domainStore))

  def getLocalInstancePath(requester: ActorPath): ActorPath = {
    requester.root / "user" / RelativePath
  }

  def acquirePersistenceProvider(requester: ActorRef, context: ActorContext, domainId: DomainId): Try[DomainPersistenceProvider] = {
    debug(s"Sending message to acquire domain persistence for $domainId by ${requester.path}")

    val path = DomainPersistenceManagerActor.getLocalInstancePath(requester.path)
    val selection = context.actorSelection(path)

    val message = AcquireDomainPersistence(domainId, requester)
    val timeout = Timeout(persistenceProviderTimeout, TimeUnit.SECONDS)

    Try {
      val f = Patterns.ask(selection, message, timeout).mapTo[DomainPersistenceProvider]
      Await.result(f, FiniteDuration(persistenceProviderTimeout, TimeUnit.SECONDS))
    }
  }

  def releasePersistenceProvider(requester: ActorRef, context: ActorContext, domainId: DomainId): Unit = {
    val path = DomainPersistenceManagerActor.getLocalInstancePath(requester.path)
    val selection = context.actorSelection(path)
    selection.tell(ReleaseDomainPersistence(domainId, requester), requester)
  }


  sealed trait Command extends CborSerializable with DomainRestMessageBody

  case class AcquireDomainPersistence(domainId: DomainId, requester: ActorRef) extends Command

  case class ReleaseDomainPersistence(domainId: DomainId, requester: ActorRef) extends Command

  case class DomainNotFoundException(domainId: DomainId) extends Exception(s"The requested domain does not exist: $domainId")

}
