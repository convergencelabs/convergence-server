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
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes
import scala.util.Failure
import com.convergencelabs.server.datastore.UserStoreActor.GetAllUsersRequest
import com.convergencelabs.server.domain.RestDomainActor.DomainMessage
import com.convergencelabs.server.datastore.UserStoreActor.GetAllUsersResponse
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.datastore.DomainStoreActor.DomainNotFound

case class DomainsResponse(ok: Boolean, domains: List[DomainFqn]) extends ResponseMessage
case class DomainResponse(ok: Boolean, domain: DomainInfo) extends ResponseMessage
case class CreateResponse(ok: Boolean) extends ResponseMessage
case class DeleteResponse(ok: Boolean) extends ResponseMessage

case class DomainInfo(
  id: String,
  displayName: String,
  namespace: String,
  domainId: String,
  owner: String)

case class CreateRequest(namespace: String, domainId: String, displayName: String)

class DomainService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainStoreActor: ActorRef,
  private[this] val domainManagerActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val domainUserService = new DomainUserService(ec, domainManagerActor, t)
  val domainCollectionService = new DomainCollectionService(ec, domainManagerActor, t)

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
          } ~
            domainUserService.route(userId, namespace, domainId) ~
            domainCollectionService.route(userId, namespace, domainId)
        }
    }
  }

  def createRequest(createRequest: CreateRequest, userId: String): Future[RestResponse] = {
    val CreateRequest(namespace, domainId, displayName) = createRequest
    (domainStoreActor ? CreateDomainRequest(namespace, domainId, displayName, userId)).mapTo[Unit].map {
      case _ => (StatusCodes.Created, CreateResponse(true))
    }
  }

  def domainsRequest(userId: String): Future[RestResponse] = {
    (domainStoreActor ? ListDomainsRequest(userId)).mapTo[ListDomainsResponse].map {
      case ListDomainsResponse(domains) => (StatusCodes.OK, DomainsResponse(
        true,
        (domains map (domain => DomainFqn(domain.domainFqn.namespace, domain.domainFqn.domainId)))))
    }
  }

  def domainRequest(namespace: String, domainId: String): Future[RestResponse] = {
    (domainStoreActor ? GetDomainRequest(namespace, domainId)).mapTo[GetDomainResponse].map {
      case GetDomainSuccess(domain) =>
        (StatusCodes.OK, DomainResponse(true, DomainInfo(
          domain.id,
          domain.displayName,
          domain.domainFqn.namespace,
          domain.domainFqn.domainId,
          domain.owner)))
      case DomainNotFound =>
        (StatusCodes.NotFound, ErrorResponse("Domain not found!"))
    }
  }

  def deleteRequest(namespace: String, domainId: String): Future[RestResponse] = {
    (domainStoreActor ? DeleteDomainRequest(namespace, domainId)) map {
      case _ => (StatusCodes.Created, DeleteResponse(true))
    }
  }
}