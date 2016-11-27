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

object RestAuthnorizationActor {
  def props(domainStore: DomainStore): Props = Props(new RestAuthnorizationActor(domainStore))

  val RelativeActorPath = "restAuthorization"

  sealed trait AuthorizationRequest
  case class DomainAuthorization(username: String, domain: DomainFqn)


  sealed trait AuthorizationResult
  case object AuthorizationGranted extends AuthorizationResult
  case object AuthorizationDenied extends AuthorizationResult
}

class RestAuthnorizationActor(domainStore: DomainStore)
    extends Actor with ActorLogging {

  def receive: Receive = {
    case DomainAuthorization(username, domain) => onDomainAuthorization(username, domain)
    case x: Any => unhandled(x)
  }

  private[this] def onDomainAuthorization(username: String, domain: DomainFqn): Unit = {
    sender ! (domainStore.getDomainByFqn(domain) match {
      case Success(Some(domain)) if domain.owner == username =>
        AuthorizationGranted
      case _ =>
        AuthorizationDenied
    })
  }
}
