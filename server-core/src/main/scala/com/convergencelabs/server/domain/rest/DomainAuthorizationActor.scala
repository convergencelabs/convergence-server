package com.convergencelabs.server.domain.rest

import scala.language.postfixOps

import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.convergence.DomainStore
import com.convergencelabs.server.datastore.convergence.PermissionsStore
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status
import com.convergencelabs.server.datastore.convergence.DomainPermissionTarget

object AuthorizationActor {
  def props(dbProvider: DatabaseProvider): Props = Props(new AuthorizationActor(dbProvider))

  val RelativePath = "AuthorizationActor"

  sealed trait AuthorizationRequest
  case class ConvergenceAuthorizedRequest(username: String, domain: DomainFqn, permission: Set[String])
}

class AuthorizationActor(private[this] val dbProvider: DatabaseProvider)
  extends Actor with ActorLogging {

  import AuthorizationActor._

  private[this] val domainStore: DomainStore = new DomainStore(dbProvider)
  private[this] val permissionsStore: PermissionsStore = new PermissionsStore(dbProvider)

  def receive: Receive = {
    case message: ConvergenceAuthorizedRequest =>
      onConvergenceAuthorizedRequest(message)
    case x: Any =>
      unhandled(x)
  }

  private[this] def onConvergenceAuthorizedRequest(message: ConvergenceAuthorizedRequest): Unit = {
    val ConvergenceAuthorizedRequest(username, domain, permissions) = message
    val target = DomainPermissionTarget(domain)
    permissionsStore.getUserPermissionsForTarget(username, target).map(_.map(_.id)).map {
      sender ! permissions.subsetOf(_)
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }
}
