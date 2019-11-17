/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.rest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _segmentStringToPathMatcher, _string2NR, as, authorize, complete, delete, entity, get, parameters, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence.NamespaceStoreActor._
import com.convergencelabs.convergence.server.domain.{Namespace, NamespaceAndDomains}
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, Permissions}
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

object NamespaceService {

  case class CreateNamespacePost(id: String, displayName: String)

  case class UpdateNamespacePut(displayName: String)

  case class NamespaceRestData(id: String, displayName: String)

  case class NamespaceAndDomainsRestData(id: String, displayName: String, domains: Set[DomainRestData])

}

class NamespaceService(private[this] val executionContext: ExecutionContext,
                       private[this] val namespaceActor: ActorRef,
                       private[this] val defaultTimeout: Timeout)
  extends JsonSupport with Logging {

  import NamespaceService._

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
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

  private[this] def getNamespaces(authProfile: AuthorizationProfile, filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    val request = GetAccessibleNamespaces(authProfile, filter, offset, limit)
    (namespaceActor ? request).mapTo[Set[NamespaceAndDomains]] map { namespaces =>
      val response = namespaces.map { n =>
        val domainData = n.domains.map(d => DomainRestData(d.displayName, d.domainFqn.namespace, d.domainFqn.domainId, d.status.toString))
        NamespaceAndDomainsRestData(n.id, n.displayName, domainData)
      }
      okResponse(response)
    }
  }

  private[this] def getNamespace(namespaceId: String): Future[RestResponse] = {
    val request = GetNamespace(namespaceId)
    (namespaceActor ? request).mapTo[Option[Namespace]] map {
      case Some(namespace) => okResponse(namespace)
      case None => notFoundResponse(Some(s"A namespace with the id '$namespaceId' does not exist"))
    }
  }

  private[this] def createNamespace(authProfile: AuthorizationProfile, create: CreateNamespacePost): Future[RestResponse] = {
    val CreateNamespacePost(id, displayName) = create
    val request = CreateNamespace(authProfile.username, id, displayName)
    (namespaceActor ? request).mapTo[Unit] map (_ => OkResponse)
  }

  private[this] def updateNamespace(authProfile: AuthorizationProfile, namespaceId: String, create: UpdateNamespacePut): Future[RestResponse] = {
    val UpdateNamespacePut(displayName) = create
    val request = UpdateNamespace(authProfile.username, namespaceId, displayName)
    (namespaceActor ? request).mapTo[Unit] map (_ => OkResponse)
  }

  private[this] def deleteNamespace(authProfile: AuthorizationProfile, namespaceId: String): Future[RestResponse] = {
    debug(s"Got request to delete namespace $namespaceId")
    val request = DeleteNamespace(authProfile.username, namespaceId)
    (namespaceActor ? request).mapTo[Unit] map (_ => OkResponse)
  }

  private[this] def canManageNamespaces(authProfile: AuthorizationProfile): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Global.ManageDomains)
  }
}
