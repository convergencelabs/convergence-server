package com.convergencelabs.server.domain

import scala.util.Success

import com.convergencelabs.server.datastore.DomainStore
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import RestAuthnorizationActor.DomainAuthorization
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationGranted
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationDenied
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationFailure
import com.convergencelabs.server.datastore.PermissionsStore
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.domain.AuthorizationActor.ConvergenceAuthorizedRequest
import akka.util.Timeout

object AuthorizationActor {
  def props(dbProvider: DatabaseProvider): Props = Props(new AuthorizationActor(dbProvider))

  val RelativeActorPath = "convergenceAuthorization"

  sealed trait AuthorizationRequest
  case class ConvergenceAuthorizedRequest(username: String, domain: DomainFqn, permission: Set[String])
}

class AuthorizationActor(private[this] val dbProvider: DatabaseProvider)
    extends Actor with ActorLogging {

  private[this] implicit val requstTimeout = Timeout(2 seconds)
  private[this] implicit val exectionContext = context.dispatcher
  
  private[this] val domainStore: DomainStore = new DomainStore(dbProvider)
  private[this] val permissionsStore: PermissionsStore = new PermissionsStore(dbProvider)

  def receive: Receive = {
    case message: ConvergenceAuthorizedRequest => onConvergenceAuthorizedRequest(message)
    case x: Any                                => unhandled(x)
  }

  private[this] def onConvergenceAuthorizedRequest(message: ConvergenceAuthorizedRequest): Unit = {
    val ConvergenceAuthorizedRequest(username, domain, permissions) = message
    val authorized = for {
      owner <- domainStore.getDomainByFqn(domain) map { domain =>
        domain match {
          case Some(domain) if domain.owner == username => true
          case _                                        => false
        }
      }

      hasPermission <- permissionsStore.getAllUserPermissions(username, domain).map(_.map(_.id)).map { permissions.subsetOf(_) }
    } yield owner || hasPermission
    
    sender ! authorized
  }
}
