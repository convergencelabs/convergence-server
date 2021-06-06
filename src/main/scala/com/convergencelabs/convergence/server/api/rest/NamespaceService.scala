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

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.backend.services.server.NamespaceStoreActor._
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, Permissions}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}


private[rest] final class NamespaceService(namespaceActor: ActorRef[Message],
                                           scheduler: Scheduler,
                                           executionContext: ExecutionContext,
                                           defaultTimeout: Timeout)
  extends JsonSupport with Logging {

  import NamespaceService._

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout
  private[this] implicit val s: Scheduler = scheduler

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("namespaces") {
      pathEnd {
        get {
          parameters("filter".?, "limit".as[Long].?, "offset".as[Long].?) { (filter, limit, offset) =>
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

  private[this] def getNamespaces(authProfile: AuthorizationProfile,
                                  filter: Option[String],
                                  offset: Option[Long],
                                  limit: Option[Long]): Future[RestResponse] = {
    namespaceActor
      .ask[GetAccessibleNamespacesResponse](GetAccessibleNamespacesRequest(authProfile.data, filter, QueryOffset(offset), QueryLimit(limit), _))
      .map(_.namespaces.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { namespaces =>
          val response = namespaces.map { n =>
            val domainData = n.domains.map(d => DomainRestData(
              d.displayName,
              d.domainId.namespace,
              d.domainId.domainId,
              d.availability.toString,
              d.status.toString,
              d.statusMessage,
              None))
            NamespaceAndDomainsRestData(n.id, n.displayName, domainData)
          }
          okResponse(response)
        })
      )
  }

  private[this] def getNamespace(namespaceId: String): Future[RestResponse] = {
    namespaceActor
      .ask[GetNamespaceResponse](GetNamespaceRequest(namespaceId, _))
      .map(_.namespace.fold(
        {
          case NamespaceNotFoundError() =>
            namespaceNotFound(namespaceId)
          case UnknownError() =>
            InternalServerError
        },
        namespace => okResponse(namespace)
      ))
  }

  private[this] def createNamespace(authProfile: AuthorizationProfile, create: CreateNamespacePost): Future[RestResponse] = {
    val CreateNamespacePost(id, displayName) = create
    namespaceActor
      .ask[CreateNamespaceResponse](CreateNamespaceRequest(authProfile.username, id, displayName, _))
      .map(_.response.fold(
        {
          case NamespaceAlreadyExistsError(field) =>
            duplicateResponse(field)
          case UnknownError() =>
            InternalServerError
        },
        _ => CreatedResponse
      ))
  }

  private[this] def updateNamespace(authProfile: AuthorizationProfile, namespaceId: String, update: UpdateNamespacePut): Future[RestResponse] = {
    val UpdateNamespacePut(displayName) = update
    namespaceActor
      .ask[UpdateNamespaceResponse](UpdateNamespaceRequest(authProfile.username, namespaceId, displayName, _))
      .map(_.response.fold(
        {
          case NamespaceNotFoundError() =>
            namespaceNotFound(namespaceId)
          case UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def deleteNamespace(authProfile: AuthorizationProfile, namespaceId: String): Future[RestResponse] = {
    namespaceActor
      .ask[DeleteNamespaceResponse](DeleteNamespaceRequest(authProfile.username, namespaceId, _))
      .map(_.response.fold(
        {
          case NamespaceNotFoundError() =>
            namespaceNotFound(namespaceId)
          case UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def namespaceNotFound(namespaceId: String): (StatusCode, RestResponseEntity) =
    notFoundResponse(Some(s"A namespace with the id '$namespaceId' does not exist"))

  private[this] def canManageNamespaces(authProfile: AuthorizationProfile): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Server.ManageDomains)
  }
}

private[rest] object NamespaceService {

  case class CreateNamespacePost(id: String, displayName: String)

  case class UpdateNamespacePut(displayName: String)

  case class NamespaceRestData(id: String, displayName: String)

  case class NamespaceAndDomainsRestData(id: String, displayName: String, domains: Set[DomainRestData])

}
