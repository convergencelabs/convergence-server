package com.convergencelabs.server.db.schema

import com.convergencelabs.server.db.schema.DatabaseManagerActor.GetConvergenceVersion
import com.convergencelabs.server.db.schema.DatabaseManagerActor.GetDomainVersion
import com.convergencelabs.server.db.schema.DatabaseManagerActor.UpgradeConvergence
import com.convergencelabs.server.db.schema.DatabaseManagerActor.UpgradeDomain
import com.convergencelabs.server.domain.DomainFqn
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

    case UpgradeConvergence(version) =>
      version match {
        case Some(v) =>
          reply(databaseManager.updagradeConvergence(v), sender)
        case None =>
          reply(databaseManager.updagradeConvergenceToLatest(), sender)
      }

    case UpgradeDomain(fqn, version) =>
      version match {
        case Some(v) =>
          reply(databaseManager.upgradeDomain(fqn, v), sender)
        case None =>
          reply(databaseManager.upgradeDomainToLatest(fqn), sender)
      }
  }
}

object DatabaseManagerActor {

  val RelativePath = "databaseManager"

  def props(schemaManager: DatabaseManager): Props = Props(new DatabaseManagerActor(schemaManager))

  case object GetConvergenceVersion
  case class GetDomainVersion(fqn: DomainFqn)

  case class UpgradeConvergence(version: Option[Int])
  case class UpgradeDomain(fqn: DomainFqn, version: Option[Int])
}
