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

import akka.actor.typed._
import akka.actor.typed.pubsub.Topic
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence.DomainStore
import com.convergencelabs.convergence.server.db.PooledDatabaseProvider
import com.convergencelabs.convergence.server.db.provision.DomainLifecycleTopic
import com.convergencelabs.convergence.server.domain.DomainId
import grizzled.slf4j.Logging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
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
 * @param baseDbUri   The base uri of the database.
 * @param domainStore The domain store to look up domain databases with.
 */
class DomainPersistenceManagerActor private(context: ActorContext[DomainPersistenceManagerActor.Message],
                                            baseDbUri: String,
                                            domainStore: DomainStore,
                                            domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage])
  extends AbstractBehavior[DomainPersistenceManagerActor.Message](context) with Logging {

  import DomainPersistenceManagerActor._

  private[this] var referenceCounts = Map[DomainId, Int]()
  private[this] var providers = Map[DomainId, DomainPersistenceProviderImpl]()
  private[this] var providersByActor = Map[ActorRef[_], List[DomainId]]()

  domainLifecycleTopic ! Topic.Subscribe(context.messageAdapter[DomainLifecycleTopic.Message] {
    case DomainLifecycleTopic.DomainDeleted(domainId) => DomainDeleted(domainId)
  })

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case Acquire(domainId, requester, replyTo) =>
        onAcquire(domainId, requester, replyTo)
      case Release(domainId, requester) =>
        onRelease(domainId, requester)
      case DomainDeleted(domainId) =>
        this.onDomainDeleted(domainId)
      case message: Register =>
        onRegister(message)
    }

    Behaviors.same
  }

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = {
    case Terminated(actor) =>
      onActorDeath(actor)
      Behaviors.same
  }

  private[this] def onRegister(register: Register): Unit = {
    implicit val scheduler: Scheduler = context.system.scheduler
    implicit val ec: ExecutionContextExecutor = context.system.executionContext
    implicit val timeout: Timeout = register.timout
    context.system.receptionist.ask[Receptionist.Registered](replyTo => Receptionist.Register(
      DomainPersistenceManagerActor.DomainPersistenceManagerServiceKey, context.self, replyTo)).map { _ =>
      register.replyTo ! Registered()
    }
  }

  private[this] def onAcquire(domainId: DomainId, requester: ActorRef[_], replyTo: ActorRef[AcquireResponse]): Unit = {
    debug(s"$domainId: Acquiring domain persistence for ${requester.path}")
    (providers.get(domainId) match {
      case Some(provider) =>
        debug(s"$domainId: domain persistence provider already exists for domain")
        Success(provider)
      case None =>
        createProvider(domainId)
    })
      .map { provider =>
        val newCount = referenceCounts.getOrElse(domainId, 0) + 1
        referenceCounts += (domainId -> newCount)

        debug(s"$domainId: Domain persistence provider has $newCount references")

        val newProviders = providersByActor.getOrElse(requester, List()) :+ domainId
        providersByActor += (requester -> newProviders)

        if (newProviders.length == 1) {
          // First time registered.  Watch this actor.
          context.watch(requester)
        }

        Acquired(provider)
      }
      .recover {
        case _: DomainNotFoundException =>
          DomainDoesNotExist(domainId)
        case cause: Throwable =>
          error(s"$domainId: Unable obtain a persistence provider", cause)
          AcquisitionFailure(cause)
      }
      .foreach(replyTo ! _)
  }

  private[this] def onRelease(domainId: DomainId, requester: ActorRef[_]): Unit = {
    debug(s"$domainId: Releasing domain persistence for ${requester.path}")

    providersByActor.get(requester) foreach { pools =>
      decrementDomainReferenceCount(domainId)

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

  private[this] def onActorDeath(terminatedActor: ActorRef[_]): Unit = {
    debug(s"Unregistering all persistence providers for terminated actor: ${terminatedActor.path}")
    context.unwatch(terminatedActor)
    providersByActor.get(terminatedActor) foreach (_.foreach(decrementDomainReferenceCount))
    providersByActor -= terminatedActor
  }

  private[this] def decrementDomainReferenceCount(domainId: DomainId): Unit = {
    referenceCounts.get(domainId) foreach (currentCount => {
      val newCount = currentCount - 1
      referenceCounts += (domainId -> newCount)

      debug(s"$domainId: Domain persistence provider has $newCount references")

      if (newCount == 0) {
        // This was the last consumer. Shut it down.
        shutdownPool(domainId)
      }
    })
  }

  private[this] def createProvider(domainId: DomainId): Try[DomainPersistenceProvider] = {
    debug(s"$domainId: Creating new persistence provider")
    domainStore.getDomainDatabase(domainId) flatMap {
      case Some(domainInfo) =>
        debug(s"$domainId: Creating new connection pool: $baseDbUri/${domainInfo.database}")

        val poolMin = context.system.settings.config.getInt("convergence.persistence.domain-databases.pool.db-pool-min")
        val poolMax = context.system.settings.config.getInt("convergence.persistence.domain-databases.pool.db-pool-max")

        val dbProvider = new PooledDatabaseProvider(baseDbUri, domainInfo.database, domainInfo.username, domainInfo.password, poolMin, poolMax)

        val provider = new DomainPersistenceProviderImpl(domainId, dbProvider)
        dbProvider.connect()
          .flatMap(_ => provider.validateConnection())
          .flatMap { _ =>
            debug(s"Successfully created connection pool for '$domainId':  $baseDbUri/${domainInfo.database}")
            providers += (domainId -> provider)
            Success(provider)
          }
      case None =>
        debug(s"$domainId: Requested to look up a domain that does not exist.")
        Failure(DomainNotFoundException(domainId))
    }
  }

  private[this] def onDomainDeleted(domainId: DomainId): Unit = {
    if (providers.contains(domainId)) {
      debug(s"$domainId: Domain deleted, shutting down connection pool")
      shutdownPool(domainId)
    }
  }

  private[this] def shutdownPool(domainId: DomainId): Unit = {
    providers.get(domainId) match {
      case Some(provider) =>
        debug(s"$domainId: Shutting down persistence provider")
        providers -= domainId
        referenceCounts -= domainId
        provider.shutdown()
      case None =>
        warn(s"$domainId: Attempted to shut down a persistence provider that was not open")
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
  private[this] val persistenceProviderTimeout = 10
  private[this] val ServiceKeyId = "domainPersistenceManager"

  private val DomainPersistenceManagerServiceKey = ServiceKey[Message](ServiceKeyId)

  def apply(baseDbUri: String,
            domainStore: DomainStore,
            domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Behavior[Message] =
    Behaviors.setup(context => new DomainPersistenceManagerActor(context, baseDbUri, domainStore, domainLifecycleTopic))

  def acquirePersistenceProvider(requester: ActorRef[_], system: ActorSystem[_], domainId: DomainId): Try[DomainPersistenceProvider] = {
    debug(s"Sending message to acquire domain persistence for $domainId by ${requester.path}")

    implicit val timeout: Timeout = Timeout(persistenceProviderTimeout, TimeUnit.SECONDS)
    implicit val scheduler: Scheduler = system.scheduler
    implicit val ec: ExecutionContextExecutor = system.executionContext

    Try {
      val f = getLocalActor(system)
        .flatMap(service => service.ask[AcquireResponse](r => Acquire(domainId, requester, r)))
      Await.result(f, FiniteDuration(persistenceProviderTimeout, TimeUnit.SECONDS))
    }.flatMap {
      case Acquired(provider) =>
        Success(provider)
      case DomainDoesNotExist(domainId) =>
        Failure(DomainNotFoundException(domainId))
      case AcquisitionFailure(cause) =>
        Failure(cause)
    }
  }

  def releasePersistenceProvider(requester: ActorRef[_], system: ActorSystem[_], domainId: DomainId): Unit = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    getLocalActor(system).foreach { service =>
      service ! Release(domainId, requester)
    }
  }

  def getLocalActor(system: ActorSystem[_]): Future[ActorRef[Message]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    implicit val timeout: Timeout = Timeout(persistenceProviderTimeout, TimeUnit.SECONDS)
    implicit val scheduler: Scheduler = system.scheduler

    system.receptionist
      .ask[Receptionist.Listing]((r: ActorRef[Receptionist.Listing]) => Receptionist.Find(DomainPersistenceManagerServiceKey, r))
      .flatMap { listing =>
        listing
          .serviceInstances(DomainPersistenceManagerServiceKey)
          .find { r =>
            r.path.address.hasLocalScope || r.path.address == system.address
          } match {
          case Some(service) =>
            Future.successful(service)
          case None =>
            Future.failed(new IllegalStateException("No local DomainPersistenceManager found"))
        }
      }
  }


  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  //
  // Note: These messages are not tagged as serializable since they should NEVER
  // be sent between actor systems.
  //

  sealed trait Message

  final case class Acquire(domainId: DomainId, requester: ActorRef[_], replyTo: ActorRef[AcquireResponse]) extends Message

  //
  // Acquire
  //
  sealed trait AcquireResponse

  final case class Acquired(provider: DomainPersistenceProvider) extends AcquireResponse

  final case class DomainDoesNotExist(domainId: DomainId) extends AcquireResponse

  final case class AcquisitionFailure(cause: Throwable) extends AcquireResponse

  //
  // Release
  //
  final case class Release(domainId: DomainId, requester: ActorRef[_]) extends Message

  //
  // DomainDeleted
  //
  private final case class DomainDeleted(domainId: DomainId) extends Message

  final case class DomainNotFoundException(domainId: DomainId) extends Exception(s"The requested domain does not exist: $domainId")

  final case class Register(timout: Timeout, replyTo: ActorRef[Registered]) extends Message

  final case class Registered()

}
