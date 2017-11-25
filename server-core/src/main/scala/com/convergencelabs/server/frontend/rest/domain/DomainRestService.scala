package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.rest.AuthorizationActor.ConvergenceAuthorizedRequest

import akka.actor.ActorRef
import akka.util.Timeout

class DomainRestService(
  executionContext: ExecutionContext,
  defaultTimeout: Timeout,
  val authorizationActor: ActorRef)
    extends JsonSupport {

  import akka.pattern.ask
  
  implicit val ec = executionContext
  implicit val t = defaultTimeout

  // Permission Checks

  def canAccessDomain(domainFqn: DomainFqn, username: String): Future[Boolean] = {
    checkPermission(domainFqn, username, Set("domain-access"))
  }

  def checkPermission(domainFqn: DomainFqn, username: String, permissions: Set[String]): Future[Boolean] = {
    val message = ConvergenceAuthorizedRequest(username, domainFqn, permissions)
    (authorizationActor ? message).mapTo[Boolean]
  }
}
