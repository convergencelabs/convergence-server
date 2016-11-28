package com.convergencelabs.server.domain

import scala.util.Success

import com.convergencelabs.server.datastore.DomainStore
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import RestAuthnorizationActor.DomainAuthorization
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationGranted
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationDenied
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationFailure

object RestAuthnorizationActor {
  def props(domainStore: DomainStore): Props = Props(new RestAuthnorizationActor(domainStore))

  val RelativeActorPath = "restAuthorization"

  sealed trait AuthorizationRequest
  case class DomainAuthorization(username: String, domain: DomainFqn)

  sealed trait AuthorizationResult
  case object AuthorizationGranted extends AuthorizationResult
  case object AuthorizationDenied extends AuthorizationResult
  case object AuthorizationFailure extends AuthorizationResult
}

class RestAuthnorizationActor(domainStore: DomainStore)
    extends Actor with ActorLogging {

  def receive: Receive = {
    case DomainAuthorization(username, domain) => onDomainAuthorization(username, domain)
    case x: Any => unhandled(x)
  }

  private[this] def onDomainAuthorization(username: String, domainFqn: DomainFqn): Unit = {
    domainStore.getDomainByFqn(domainFqn) map { domain =>
      domain match {
        case Some(domain) if domain.owner == username =>
          sender ! AuthorizationGranted
        case _ =>
          sender ! AuthorizationDenied
      }
    } recover {
      case cause: Exception =>
        log.error(cause, "Error authorizing user for domain.")
        sender ! AuthorizationFailure
    }
  }
}
