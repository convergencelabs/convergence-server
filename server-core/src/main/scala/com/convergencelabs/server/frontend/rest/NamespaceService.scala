package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor.CreateNamespace
import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor.DeleteNamespace
import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor.GetAccessibleNamespaces
import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor.GetNamespace
import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor.UpdateNamespace
import com.convergencelabs.server.domain.Namespace
import com.convergencelabs.server.domain.NamespaceAndDomains
import com.convergencelabs.server.security.AuthorizationProfile
import com.convergencelabs.server.security.Permissions

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives._string2NR
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.authorize
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.util.Timeout
import grizzled.slf4j.Logging

object NamespaceService {
  case class CreateNamespacePost(id: String, displayName: String)
  case class UpdateNamespacePut(displayName: String)
  case class NamespaceRestData(id: String, displayName: String)
  case class NamespaceAndDomainsRestData(id: String, displayName: String, domains: Set[DomainRestData])
}

class NamespaceService(
  private[this] val executionContext: ExecutionContext,
  private[this] val namespaceActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
  extends JsonSupport
  with Logging {

  import NamespaceService._
  import akka.pattern.ask
  import akka.http.scaladsl.server.Directives.Segment

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { authProfile: AuthorizationProfile =>
    pathPrefix("namespaces") {
      pathEnd {
        get {
          parameters("filter".?, "limit".as[Int].?, "offset".as[Int].?) { (filter, limit, offset) =>
            complete(getNamespaces(authProfile, filter, offset, limit))
          }
        } ~ post {
          authorize(canManageNamespaces(authProfile)) {
            entity(as[CreateNamespacePost]) { request =>
              complete(createNamespace(authProfile, request))
            }
          }
        }
      } ~ pathPrefix(Segment) { namespace =>
        pathEnd {
          get {
            complete(getNamespace(namespace))
          } ~ put {
            authorize(canManageNamespaces(authProfile)) {
              entity(as[UpdateNamespacePut]) { request =>
                complete(updateNamespace(authProfile, namespace, request))
              }
            }
          } ~ delete {
            authorize(canManageNamespaces(authProfile)) {
              complete(deleteNamespace(authProfile, namespace))
            }
          }
        }
      }
    }
  }

  def getNamespaces(authProfile: AuthorizationProfile, filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    val request = GetAccessibleNamespaces(authProfile, filter, offset, limit)
    (namespaceActor ? request).mapTo[Set[NamespaceAndDomains]] map { namespaces =>
      val response = namespaces.map { n =>
        val domainData = n.domains.map(d => DomainRestData(d.displayName, d.domainFqn.namespace, d.domainFqn.domainId, d.status.toString))
        NamespaceAndDomainsRestData(n.id, n.displayName, domainData)
      }
      okResponse(response)
    }
  }

  def getNamespace(namespaceId: String): Future[RestResponse] = {
    val request = GetNamespace(namespaceId)
    (namespaceActor ? request).mapTo[Option[Namespace]] map {
      _ match {
        case Some(namespace) => okResponse(namespace)
        case None => notFoundResponse(Some(s"A namespaec with the id '${namespaceId}' does not exist"))
      }
    }
  }

  def createNamespace(authProfile: AuthorizationProfile, create: CreateNamespacePost): Future[RestResponse] = {
    val CreateNamespacePost(id, displayName) = create
    val request = CreateNamespace(authProfile.username, id, displayName)
    (namespaceActor ? request).mapTo[Unit] map (_ => OkResponse)
  }

  def updateNamespace(authProfile: AuthorizationProfile, namespaceId: String, create: UpdateNamespacePut): Future[RestResponse] = {
    val UpdateNamespacePut(displayName) = create
    val request = UpdateNamespace(authProfile.username, namespaceId, displayName)
    (namespaceActor ? request).mapTo[Unit] map (_ => OkResponse)
  }

  def deleteNamespace(authProfile: AuthorizationProfile, namespaceId: String): Future[RestResponse] = {
    debug(s"Got request to delete namespace ${namespaceId}")
    val request = DeleteNamespace(authProfile.username, namespaceId)
    (namespaceActor ? request).mapTo[Unit] map (_ => OkResponse)
  }

  def canManageNamespaces(authProfile: AuthorizationProfile): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Global.ManageDomains)
  }
}
