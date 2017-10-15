package com.convergencelabs.server.domain.model.cluster

import akka.actor.Actor
import akka.actor.ActorLogging
import scala.concurrent.duration.Duration
import akka.actor.ReceiveTimeout
import akka.cluster.sharding.ShardRegion.Passivate
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.datastore.domain.ModelOperationProcessor
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore
import scala.util.Try
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import scala.util.control.NonFatal
import scala.util.Failure
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider

class RealTimeModelActor(
  val domainFqn: DomainFqn,
  val receiveTimeout: Duration)
    extends Actor
    with ActorLogging {

  private[this] var provider: Option[DomainPersistenceProvider] = None
  private[this] var inMemoryModel: Option[InMemoryRealTimeModel] = None
  private[this] var id: Option[String] = None

  def receive: Receive = receiveUnintialized

  private[this] def receiveUnintialized: Receive = {
    case msg: RealTimeModelMessage =>
      initialize(msg.modelId).map(_ => receiveClosed(msg))
    case msg: Any =>
      unhandled(msg)
  }

  private[this] def modelId(): String = {
    this.id.getOrElse {
      throw new IllegalStateException("Can not get the model id before the actor is initialized.")
    }
  }
  
  private[this] def persistentProvider: DomainPersistenceProvider = {
    this.provider.getOrElse {
      throw new IllegalStateException("Can not get the persistence provider before the actor is initialized.")
    }
  }

  private[this] def receiveClosed: Receive = {
    case msg: RestModelMessage =>
    // handle rest calls
    case msg: OpenRealTimeModel =>
      this.becomeOpened()
    case msg: RealTimeModelMessage =>
    // not valid because we can't do real time stuff excpect open
    case msg: ReceiveTimeout =>
      this.passivate()
    case msg: Any =>
      unhandled(msg)
  }

  private[this] def receiveOpened: Receive = {
    case msg: RealTimeModelMessage =>
    // pass to model
    case msg: ReceiveTimeout =>
    // Ignore
    case msg: Any =>
      unhandled(msg)
  }

  private[this] def receivePassivating: Receive = {
    case msg: ReceiveTimeout =>
    // ignore
    case msg: RealTimeModelMessage =>
      // Forward this back to the shard region, it will be handled by the next actor that is stood up.
      this.context.parent.forward(msg)
    case msg: Any =>
      unhandled(msg)
  }

  private[this] def becomeOpened(): Unit = {
    log.debug("Model '{}/{}' becoming open.", domainFqn, modelId())
    this.inMemoryModel = Some(new InMemoryRealTimeModel(
        () => this.becomeClosed(),
        persistentProvider.modelStore,
        persistentProvider.modelOperationProcessor,
        persistentProvider.modelSnapshotStore
        ))
    this.context.become(receiveOpened)
    this.context.setReceiveTimeout(Duration.Undefined)
  }

  private[this] def becomeClosed(): Unit = {
    log.debug("Model '{}/{}' becoming closed.", domainFqn, modelId())
    this.inMemoryModel = None
    this.context.become(receiveClosed)
    this.context.setReceiveTimeout(this.receiveTimeout)
  }

  private[this] def passivate(): Unit = {
    log.debug("Model '{}/{}' passivating.", modelId(), domainFqn)
    this.context.parent ! Passivate
    this.context.setReceiveTimeout(Duration.Undefined)
    this.context.become(receivePassivating)
  }
  
  private[this] def initialize(id: String): Try[Unit] = {
    log.debug(s"Real Time Model Actor initializing: '{}/{}'", domainFqn, id)
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) map { provider =>
      log.debug(s"Real Time Model Actor aquired persistence: '{}/{}'", domainFqn, id)
      this.id = Some(id)
      this.provider = Some(provider)
      context.become(receiveClosed)
      ()
    } recoverWith {
      case NonFatal(cause) =>
        log.debug(s"Error initializing Real Time Model Actor: '{}/{}'", domainFqn, id)
        Failure(cause)
    }
  }
}