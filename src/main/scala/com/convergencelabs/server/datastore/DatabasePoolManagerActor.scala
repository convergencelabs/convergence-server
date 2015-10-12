package com.convergencelabs.server.datastore

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

object DatabasePoolManagerActor {
  def props(
    domainConfigStore: DomainConfigurationStore): Props = Props(
    new DatabasePoolManagerActor(domainConfigStore))
}

class DatabasePoolManagerActor(domainConfigStore: DomainConfigurationStore) extends Actor with ActorLogging {

  private[this] var refernceCounts = Map[DomainFqn, Int]()
  private[this] var pools = Map[DomainFqn, OPartitionedDatabasePool]()
  private[this] var poolsByActor = Map[ActorRef, List[DomainFqn]]()

  def receive = {
    case AcquirePool(domainFqn) => onAcquire(domainFqn)
    case ReleasePool(domainFqn) => onRelease(domainFqn)
    case Terminated(actor) => onActorDeath(actor)
  }

  private[this] def onAcquire(domainFqn: DomainFqn): Unit = {
    val pool = pools.get(domainFqn) match {
      case Some(pool) => Success(pool)
      case None => createPool(domainFqn)
    }

    pool match {
      case Success(pool) => {
        val newCount = refernceCounts.getOrElse(domainFqn, 0) + 1
        refernceCounts = refernceCounts + (domainFqn -> newCount)

        val newPools = poolsByActor.getOrElse(sender, List()) :+ domainFqn
        poolsByActor = poolsByActor + (sender -> newPools)

        if (newPools.length == 1) {
          // First time registered.  Watch this actor.
          context.watch(sender)
        }

        sender ! PoolReference(pool)
      }
      case Failure(cause) => {
        sender ! PoolUnavailable
      }
    }
  }

  private[this] def onRelease(domainFqn: DomainFqn): Unit = {
    decrementCount(domainFqn)

    val acquiredPools = poolsByActor.get(sender)
    if (acquiredPools.isDefined) {
      val pools = acquiredPools.get
      val newPools = pools diff List(domainFqn)

      if (newPools.length == 0) {
        poolsByActor = poolsByActor - sender
        // This actor no longer has any connections open.
        context.unwatch(sender)
      } else {
        poolsByActor = poolsByActor + (sender -> newPools)
      }
    }
  }

  private[this] def onActorDeath(actor: ActorRef): Unit = {
    val acquiredPools = poolsByActor.get(sender)
    if (acquiredPools.isDefined) {

      acquiredPools.get foreach (domainFqn => {
        decrementCount(domainFqn)
      })

      poolsByActor = poolsByActor - sender
      context.unwatch(sender)
    }
  }

  private[this] def decrementCount(domainFqn: DomainFqn): Unit = {
    val currentCount = refernceCounts.get(domainFqn)
    if (currentCount.isDefined) {
      val newCount = currentCount.get - 1

      if (newCount == 0) {
        shutdownPool(pools(domainFqn))
      } else {
        // decrement
        refernceCounts = refernceCounts + (domainFqn -> newCount)
      }
    }
  }

  private[this] def createPool(domainFqn: DomainFqn): Try[OPartitionedDatabasePool] = Try({
    val config = domainConfigStore.getDomainConfig(domainFqn)
    val DomainDatabaseConfig(uri, username, password) = config.dbConfig
    new OPartitionedDatabasePool(uri, username, password)
  })

  private[this] def shutdownPool(pool: OPartitionedDatabasePool): Unit = {
    pool.close()
  }
}

case class AcquirePool(domainFqn: DomainFqn)
case class ReleasePool(domainFqn: DomainFqn)
case class PoolReference(pool: OPartitionedDatabasePool)
case object PoolUnavailable