package com.convergencelabs.server.frontend.rest

import akka.http.scaladsl.server.Directives._
import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import akka.util.Timeout
import scala.concurrent.Future
import akka.pattern.ask

case class DomainsResponse(ok: Boolean, domains: List[DomainFqn])
case class DomainResponse(ok: Boolean, domain: Option[DomainInfo])

class DomainService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonService {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { userId: String =>
    pathPrefix("domains") {
      pathEnd {
        get {
          complete { domainsRequest(userId) }
        }
      } ~
        path(Segment) { domainId =>
          get {
            complete { domainRequest(domainId) }
          }
        }
    }
  }

  def domainsRequest(userId: String): Future[DomainsResponse] = {
    (domainActor ? DomainsRequest).mapTo[List[_]].map {
      case x: List[DomainFqn] => DomainsResponse(true, x)
    }
  }

  def domainRequest(domainId: String): Future[DomainResponse] = {
    (domainActor ? DomainRequest(domainId)).mapTo[Option[DomainInfo]].map {
      case Some(d) => DomainResponse(true, Some(d))
      case None    => DomainResponse(true, None)
    }
  }
}