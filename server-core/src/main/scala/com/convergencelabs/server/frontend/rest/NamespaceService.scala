package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.CreateConvergenceUserRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.DeleteConvergenceUserRequest
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.GetConvergenceUser
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.GetConvergenceUsers
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.InvalidValueExcpetion

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives._string2NR
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.handleWith
import akka.http.scaladsl.server.Directives.handleExceptions
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.as
import akka.util.Timeout
import akka.http.scaladsl.server.ExceptionHandler
import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor.GetAccessibleNamespaces
import com.convergencelabs.server.domain.Namespace
import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor.CreateNamespace
import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor.DeleteNamespace
import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor.UpdateNamespace

object NamespaceService {
  case class CreateNamespacePost(id: String, displayName: String)
  case class UpdateNamespacePut(displayName: String)
  case class NamespaceRestData(id: String, displayName: String)
}

class NamespaceService(
  private[this] val executionContext: ExecutionContext,
  private[this] val namespaceActor: ActorRef,
  private[this] val defaultTimeout: Timeout) extends JsonSupport {

  import NamespaceService._
  import akka.pattern.ask

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { username: String =>
    pathPrefix("namespaces") {
      pathEnd {
        get {
          complete(getNamespaces(username))
        } ~ post {
          entity(as[CreateNamespacePost]) { request =>
            complete(createNamespace(username, request))
          }
        }
      } ~ pathPrefix(Segment) { namespace =>
        pathEnd {
          put {
            entity(as[UpdateNamespacePut]) { request =>
              complete(updateNamespace(username, namespace, request))
            }
          } ~ delete {
            complete(deleteNamespace(username, namespace))
          }
        }
      }
    }
  }

  def getNamespaces(username: String): Future[RestResponse] = {
    val request = GetAccessibleNamespaces(username)
    (namespaceActor ? request).mapTo[List[Namespace]] map { namespaces =>
      val response = namespaces.map(n => NamespaceRestData(n.id, n.displayName))
      okResponse(response)
    }
  }

  def createNamespace(requestor: String, create: CreateNamespacePost): Future[RestResponse] = {
    val CreateNamespacePost(id, displayName) = create
    val request = CreateNamespace(requestor, id, displayName)
    (namespaceActor ? request).mapTo[Unit] map (_ => OkResponse)
  }

  def updateNamespace(requestor: String, namespaceId: String, create: UpdateNamespacePut): Future[RestResponse] = {
    val UpdateNamespacePut(displayName) = create
    val request = UpdateNamespace(requestor, namespaceId, displayName)
    (namespaceActor ? request).mapTo[Unit] map (_ => OkResponse)
  }

  def deleteNamespace(requestor: String, namespaceId: String): Future[RestResponse] = {
    val request = DeleteNamespace(requestor, namespaceId)
    (namespaceActor ? request).mapTo[Unit] map (_ => OkResponse)
  }
}
