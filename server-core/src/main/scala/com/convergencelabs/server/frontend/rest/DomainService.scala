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
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainFailure
import com.convergencelabs.server.datastore.UserStoreActor.GetAllUsersRequest
import com.convergencelabs.server.domain.RestDomainActor.DomainMessage
import com.convergencelabs.server.datastore.UserStoreActor.GetAllUsersResponse
import com.convergencelabs.server.domain.DomainUser

case class DomainsResponse(ok: Boolean, domains: List[DomainFqn]) extends ResponseMessage
case class DomainResponse(ok: Boolean, domain: DomainInfo) extends ResponseMessage
case class CreateResponse(ok: Boolean) extends ResponseMessage
case class DeleteResponse(ok: Boolean) extends ResponseMessage
case class ListUsersResponse(ok: Boolean, users: List[DomainUser]) extends ResponseMessage

case class DomainInfo(
  id: String,
  displayName: String,
  namespace: String,
  domainId: String,
  owner: String)

case class CreateRequest(namespace: String, domainId: String, displayName: String)

class DomainService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainActor: ActorRef,
  private[this] val domainManagerActor: ActorRef,
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
          } ~ pathPrefix("users") {
            get {
              complete(getAllUsersRequest(namespace, domainId))
            }
          }
        }
    }
  }

  def createRequest(createRequest: CreateRequest, userId: String): Future[RestResponse] = {
    val CreateRequest(namespace, domainId, displayName) = createRequest
    (domainActor ? CreateDomainRequest(namespace, domainId, displayName, userId)).mapTo[Try[Unit]].map {
      case Success(_) => (StatusCodes.Created, CreateResponse(true))
      case Failure(error) => InternalServerError
    }
  }

  def domainsRequest(userId: String): Future[RestResponse] = {
    (domainActor ? ListDomainsRequest(userId)).mapTo[Try[ListDomainsResponse]].map {
      case Success(ListDomainsResponse(domains)) => (StatusCodes.OK, DomainsResponse(
        true,
        (domains map (domain => DomainFqn(domain.domainFqn.namespace, domain.domainFqn.domainId)))))
      case Failure(error) => InternalServerError
    }
  }

  def domainRequest(namespace: String, domainId: String): Future[RestResponse] = {
    (domainActor ? GetDomainRequest(namespace, domainId)).mapTo[Try[GetDomainResponse]].map {
      case Success(GetDomainSuccess(domain)) => 
        (StatusCodes.OK, DomainResponse(true, DomainInfo(
            domain.id, 
            domain.displayName, 
            domain.domainFqn.namespace, 
            domain.domainFqn.domainId,
            domain.owner)))
      case Success(GetDomainFailure) => 
        (StatusCodes.NotFound, ErrorResponse("Domain not found!"))
      case Failure(error) => InternalServerError
    }
  }

  def deleteRequest(namespace: String, domainId: String): Future[RestResponse] = {
    (domainActor ? DeleteDomainRequest(namespace, domainId)).mapTo[Try[Unit]].map {
      case Success(_) => (StatusCodes.Created, DeleteResponse(true))
      case Failure(error) => InternalServerError
    }
  }

  def getAllUsersRequest(namespace: String, domainId: String): Future[RestResponse] = {
    (domainActor ? DomainMessage(DomainFqn(namespace, domainId), GetAllUsersRequest())).mapTo[Try[GetAllUsersResponse]].map {
      case Success(GetAllUsersResponse(users)) => (StatusCodes.OK, ListUsersResponse(true, users))
      case Failure(error) => InternalServerError
    }
  }
}