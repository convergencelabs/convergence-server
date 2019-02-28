package com.convergencelabs.server.db.schema

import scala.util.Success

import com.convergencelabs.server.db.schema.DatabaseManagerActor.GetConvergenceVersion
import com.convergencelabs.server.db.schema.DatabaseManagerActor.GetDomainVersion
import com.convergencelabs.server.db.schema.DatabaseManagerActor.UpgradeConvergence
import com.convergencelabs.server.db.schema.DatabaseManagerActor.UpgradeDomain
import com.convergencelabs.server.db.schema.DatabaseManagerActor.UpgradeDomains
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.util.ReplyUtil

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props

class DatabaseManagerActor(private[this] val databaseManager: DatabaseManager)
    extends Actor
    with ActorLogging
    with ReplyUtil {

  def receive: Receive = {
    case GetConvergenceVersion =>
      reply(databaseManager.getConvergenceVersion(), sender)

    case GetDomainVersion(fqn) =>
      reply(databaseManager.getDomainVersion(fqn), sender)

    case UpgradeConvergence(version, preRelease) =>
      reply(Success(()), sender)

      version match {
        case Some(v) =>
          databaseManager.updagradeConvergence(v, preRelease)
        case None =>
          databaseManager.updagradeConvergenceToLatest(preRelease)
      }

    case UpgradeDomain(fqn, version, preRelease) =>
      reply(Success(()), sender)

      version match {
        case Some(v) =>
          databaseManager.upgradeDomain(fqn, v, preRelease)
        case None =>
          databaseManager.upgradeDomainToLatest(fqn, preRelease)
      }

    case UpgradeDomains(version, preRelease) =>
      reply(Success(()), sender)

      version match {
        case Some(v) =>
          databaseManager.upgradeAllDomains(v, preRelease)
        case None =>
          databaseManager.upgradeAllDomainsToLatest(preRelease)
      }
  }
}

object DatabaseManagerActor {

  val RelativePath = "databaseManager"

  def props(schemaManager: DatabaseManager): Props = Props(new DatabaseManagerActor(schemaManager))

  case object GetConvergenceVersion
  case class GetDomainVersion(fqn: DomainId)

  case class UpgradeConvergence(version: Option[Int], preRelease: Boolean)
  case class UpgradeDomain(fqn: DomainId, version: Option[Int], preRelease: Boolean)
  case class UpgradeDomains(version: Option[Int], preRelease: Boolean)
}
