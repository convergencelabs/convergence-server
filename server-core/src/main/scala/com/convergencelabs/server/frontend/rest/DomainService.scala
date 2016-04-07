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
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainSuccess
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainResponse
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.DeleteDomainRequest

case class DomainsResponse(ok: Boolean, domains: List[DomainFqn])
case class DomainResponse(ok: Boolean, domain: Option[Domain])
case class CreateResponse(ok: Boolean)
case class DeleteResponse(ok: Boolean)

case class CreateRequest(namespace: String, domainId: String, displayName: String)

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
          complete(domainsRequest(userId))
        } ~
        post {
          entity(as[CreateRequest]) { request =>
            complete(createRequest(request, userId))
          }
        }
      } ~
        pathPrefix(Segment / Segment) { (namespace, domainId) =>
          pathEnd {
            get {
              complete(domainRequest(namespace, domainId))
            } ~
            delete {
              complete(deleteRequest(namespace, domainId))
            }
          }
        }
    }
  }

  def createRequest(createRequest: CreateRequest, userId: String): Future[CreateResponse] = {
    val CreateRequest(namespace, domainId, displayName) = createRequest
    (domainActor ? CreateDomainRequest(namespace, domainId, displayName, userId)).mapTo[Try[Unit]].map {
      case Success(_) => CreateResponse(true)
      case _          => CreateResponse(false)
    }
  }

  def domainsRequest(userId: String): Future[DomainsResponse] = {
    (domainActor ? ListDomainsRequest(userId)).mapTo[Try[ListDomainsResponse]].map {
      case Success(ListDomainsResponse(domains)) => DomainsResponse(true, (domains map (domain => DomainFqn(domain.domainFqn.namespace, domain.domainFqn.domainId))))
      case _                                     => DomainsResponse(false, List())
    }
  }

  def domainRequest(namespace: String, domainId: String): Future[DomainResponse] = {
    (domainActor ? GetDomainRequest(namespace, domainId)).mapTo[Try[GetDomainResponse]].map {
      case Success(GetDomainSuccess(domain)) => DomainResponse(true, Some(domain))
      case _                                 => DomainResponse(false, None)
    }
  }

  def deleteRequest(namespace: String, domainId: String): Future[DeleteResponse] = {
    (domainActor ? DeleteDomainRequest(namespace, domainId)).mapTo[Try[Unit]].map {
      case Success(_) => DeleteResponse(true)
      case _          => DeleteResponse(false)
    }
  }
}