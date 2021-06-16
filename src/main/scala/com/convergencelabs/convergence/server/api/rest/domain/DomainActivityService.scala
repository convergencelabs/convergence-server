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

package com.convergencelabs.convergence.server.api.rest.domain

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _string2NR, as, complete, delete, entity, get, parameters, pathEnd, pathPrefix, post}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.api.rest.domain.DomainActivityService.CreateActivityData
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{GroupPermissions, UserPermissions, WorldPermission}
import com.convergencelabs.convergence.server.backend.services.domain.activity.{ActivityActor, ActivityServiceActor}
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityActor.{CreateRequest, CreateResponse, DeleteRequest, DeleteResponse}
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityServiceActor.{GetActivitiesRequest, GetActivitiesResponse, GetActivityRequest, GetActivityResponse}
import com.convergencelabs.convergence.server.backend.services.domain.permissions.AllPermissions
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.activity.ActivityId
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}

import scala.concurrent.{ExecutionContext, Future}

private[domain] final class DomainActivityService(domainRestActor: ActorRef[DomainRestActor.Message],
                                                  activityShardRegion: ActorRef[ActivityActor.Message],
                                                  scheduler: Scheduler,
                                                  executionContext: ExecutionContext,
                                                  timeout: Timeout)
  extends AbstractDomainRestService(scheduler, executionContext, timeout) {

  def route(authProfile: AuthorizationProfile, domainId: DomainId): Route = {
    pathPrefix("activities") {
      pathEnd {
        get {
          parameters("type".?, "id".?, "limit".as[Long].?, "offset".as[Long].?) { (typeFilter, idFilter, limit, offset) =>
            complete(getActivities(domainId, typeFilter, idFilter, limit, offset))
          }
        } ~ post {
          entity(as[CreateActivityData]) { activityData =>
            complete(createActivity(authProfile, domainId, activityData))
          }
        }
      } ~ pathPrefix(Segment / Segment) { (activityType, activityId) =>
        val id = ActivityId(activityType, activityId)
        pathEnd {
          get {
            complete(getActivity(domainId, id))
          } ~ delete {
            complete(deleteActivity(authProfile, domainId, id))
          }
        }
      }
    }
  }

  private[this] def createActivity(authProfile: AuthorizationProfile, domainId: DomainId, data: CreateActivityData): Future[RestResponse] = {
    val CreateActivityData(activityType, activityId, world, user, group) = data
    val id = ActivityId(activityType, activityId)

    val worldPermissions = DomainActivityService.toPermissionStrings(world).map(WorldPermission).toSet

    val userPermissions = user.map { case (userId, permissions) =>
      UserPermissions(DomainUserId.normal(userId), DomainActivityService.toPermissionStrings(permissions).toSet)
    }.toSet

    val groupPermissions = user.map { case (groupId, permissions) =>
      GroupPermissions(groupId, DomainActivityService.toPermissionStrings(permissions).toSet)
    }.toSet

    val allPermissions = AllPermissions(worldPermissions, userPermissions, groupPermissions)

    activityShardRegion.ask[CreateResponse](r => CreateRequest(domainId, id, None, allPermissions, r))
      .map(_.response.fold(
        {
          case ActivityActor.AlreadyExists() =>
            conflictsResponse("id", activityId)
          case ActivityActor.UnauthorizedError(msg) =>
            forbiddenResponse(msg)
          case ActivityActor.UnknownError() =>
            InternalServerError
        },
        { _ => OkResponse}
      ))
  }

  private[this] def deleteActivity(authProfile: AuthorizationProfile, domainId: DomainId, activityId: ActivityId): Future[RestResponse] = {
    activityShardRegion.ask[DeleteResponse](r => DeleteRequest(domainId, activityId, None, r))
      .map(_.response.fold(
        {
          case ActivityActor.NotFoundError() =>
            NotFoundResponse
          case ActivityActor.UnauthorizedError(msg) =>
            forbiddenResponse(msg)
          case ActivityActor.UnknownError() =>
            InternalServerError
        },
        { _ => DeletedResponse}
      ))
  }

  private[this] def getActivities(domainId: DomainId, typeFilter: Option[String], idFilter: Option[String], limit: Option[Long], offset: Option[Long]): Future[RestResponse] = {
    domainRestActor
      .ask[GetActivitiesResponse](
        r => DomainRestMessage(domainId, GetActivitiesRequest(domainId, typeFilter, idFilter, QueryLimit(limit), QueryOffset(offset), r)))
      .map(_.activities.fold(
        {
          case ActivityServiceActor.UnknownError() =>
            InternalServerError
        },
        { pagedData =>
          okResponse(PagedRestResponse(pagedData.data, pagedData.offset, pagedData.count))
        }
      ))
  }

  private[this] def getActivity(domainId: DomainId, activityId: ActivityId): Future[RestResponse] = {
    domainRestActor
      .ask[GetActivityResponse](
        r => DomainRestMessage(domainId, GetActivityRequest(domainId, activityId, r)))
      .map(_.activity.fold(
        {
          case ActivityServiceActor.ActivityNotFound() =>
            NotFoundResponse
          case ActivityServiceActor.UnknownError() =>
            InternalServerError
        },
        { activity =>
          okResponse(activity)
        })
      )
  }
}

object DomainActivityService {
  case class CreateActivityData(activityType: String,
                                activityId: String,
                                worldPermissions: ActivityPermissionsRestData,
                                userPermissions: Map[String, ActivityPermissionsRestData],
                                groupPermissions: Map[String, ActivityPermissionsRestData])

  case class ActivityPermissionsRestData(join: Boolean, viewState: Boolean, setState: Boolean, manage: Boolean)

  def toPermissionStrings(data: ActivityPermissionsRestData): Seq[String] = {
    val result = scala.collection.mutable.Set[String]()
    if (data.join) {
      result += "join"
    }

    if (data.manage) {
      result += "manage"
    }

    if (data.setState) {
      result += "set_state"
    }

    if (data.viewState) {
      result += "view_state"
    }

    result.toSeq
  }
}
