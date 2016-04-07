package com.convergencelabs.server.frontend.rest

import akka.http.scaladsl.server.Directives._
import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import akka.util.Timeout
import scala.concurrent.Future
import akka.pattern.ask
import com.convergencelabs.server.datastore.DomainStoreActor
import com.convergencelabs.server.datastore.DomainStoreActor.ListDomainsRequest
import com.convergencelabs.server.datastore.DomainStoreActor.ListDomainsResponse
import scala.util.Try
import scala.util.Success
import com.convergencelabs.server.domain.DomainFqn

case class DomainsResponse(ok: Boolean, domains: List[DomainFqn])
case class DomainResponse(ok: Boolean, domain: Option[DomainInfo])
case class DomainInfo(namespace: String, domain: String, title: String)
case class DomainRequest(domainId: String)


class DomainService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

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
    (domainActor ? ListDomainsRequest).mapTo[Try[ListDomainsResponse]].map {
      case Success(ListDomainsResponse(domains)) => DomainsResponse(true, (domains map (domain => DomainFqn(domain.domainFqn.namespace, domain.domainFqn.domainId))))
      case _ => DomainsResponse(false, List())
    }
  }

  def domainRequest(domainId: String): Future[DomainResponse] = {
    (domainActor ? DomainRequest(domainId)).mapTo[Option[DomainInfo]].map {
      case Some(d) => DomainResponse(true, Some(d))
      case None    => DomainResponse(true, None)
    }
  }
}