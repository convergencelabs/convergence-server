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
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _string2NR, as, complete, delete, entity, get, parameters, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.api.rest.domain.DomainActivityService._
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityActor.{CreateRequest, CreateResponse, DeleteRequest, DeleteResponse}
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityServiceActor.{GetActivitiesRequest, GetActivitiesResponse, GetActivityRequest, GetActivityResponse}
import com.convergencelabs.convergence.server.backend.services.domain.activity.{ActivityActor, ActivityPermission, ActivityServiceActor}
import com.convergencelabs.convergence.server.backend.services.domain.permissions.{AllPermissions, SetPermissions}
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.activity.{Activity, ActivityId}
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
            complete(createActivity(domainId, activityData))
          }
        }
      } ~ pathPrefix(Segment / Segment) { (activityType, activityId) =>
        val id = ActivityId(activityType, activityId)
        pathEnd {
          get {
            complete(getActivity(domainId, id))
          } ~ delete {
            complete(deleteActivity(domainId, id))
          }
        } ~ pathPrefix("permissions") {
          pathEnd {
            get {
              complete(getPermissions(domainId, id))
            } ~ put {
              entity(as[SetPermissionsRestData]) { permissions =>
                complete(setPermissions(domainId, id, permissions))
              }
            }
          }
        }
      }
    }
  }

  private[this] def createActivity(domainId: DomainId, data: CreateActivityData): Future[RestResponse] = {
    val CreateActivityData(activityType, activityId, world, user, group) = data
    val id = ActivityId(activityType, activityId)

    val worldPermissions = DomainActivityService.toPermissionStrings(world)

    val userPermissions = user.map { case (userId, permissions) =>
      DomainUserId.normal(userId) -> DomainActivityService.toPermissionStrings(permissions)
    }

    val groupPermissions = group.map { case (groupId, permissions) =>
      groupId -> DomainActivityService.toPermissionStrings(permissions)
    }

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
        { _ => OkResponse }
      ))
  }

  private[this] def deleteActivity(domainId: DomainId, activityId: ActivityId): Future[RestResponse] = {
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
        { _ => DeletedResponse }
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
          val data = pagedData.data.map(toActivityData)
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

  private[this] def getPermissions(domainId: DomainId, activityId: ActivityId): Future[RestResponse] = {
    activityShardRegion.ask[ActivityActor.GetPermissionsResponse](r => ActivityActor.GetPermissionsRequest(domainId, activityId, None, r))
      .map(_.permissions.fold(
        {
          case ActivityActor.UnauthorizedError(msg) =>
            forbiddenResponse(msg)
          case ActivityActor.UnknownError() =>
            InternalServerError
          case ActivityActor.NotFoundError() =>
            NotFoundResponse
        },
        { permissions =>
          val worldPermissions = stringsToPermissions(permissions.world)
          val userPermissions = permissions.user.map { case (userId, permissions) =>
            userId.username -> stringsToPermissions(permissions)
          }

          val groupPermissions = permissions.group.map { case (groupId, permissions) =>
            groupId -> stringsToPermissions(permissions)
          }

          val response = AllPermissionsRestData(worldPermissions, userPermissions, groupPermissions)
          okResponse(response)
        }
      ))
  }

  private[this] def setPermissions(domainId: DomainId,
                                   activityId: ActivityId,
                                   permissions: SetPermissionsRestData): Future[RestResponse] = {
    val SetPermissionsRestData(world, user, group) = permissions

    val worldPermissions = world.map(toPermissionStrings)
    val userPermissions = user.map(_.map(p => (DomainUserId.normal(p._1), toPermissionStrings(p._2))))
    val groupPermissions = group.map(_.map(p => (p._1, toPermissionStrings(p._2))))

    val setPermissions = SetPermissions(worldPermissions, userPermissions, groupPermissions)

    activityShardRegion.ask[ActivityActor.SetPermissionsResponse](
      r => ActivityActor.SetPermissionsRequest(domainId, activityId, None, setPermissions, r))
      .map(_.response.fold(
        {
          case ActivityActor.UnauthorizedError(msg) =>
            forbiddenResponse(msg)
          case ActivityActor.UnknownError() =>
            InternalServerError
          case ActivityActor.NotFoundError() =>
            NotFoundResponse
        },
        { _ => OkResponse }
      ))
  }
}

object DomainActivityService {
  case class CreateActivityData(activityType: String,
                                activityId: String,
                                worldPermissions: ActivityPermissionsRestData,
                                userPermissions: Map[String, ActivityPermissionsRestData],
                                groupPermissions: Map[String, ActivityPermissionsRestData])

  case class AllPermissionsRestData(worldPermissions: ActivityPermissionsRestData,
                                    userPermissions: Map[String, ActivityPermissionsRestData],
                                    groupPermissions: Map[String, ActivityPermissionsRestData]
                                   )

  case class SetPermissionsRestData(worldPermissions: Option[ActivityPermissionsRestData],
                                    userPermissions: Option[Map[String, ActivityPermissionsRestData]],
                                    groupPermissions: Option[Map[String, ActivityPermissionsRestData]]
                                   )

  case class ActivityPermissionsRestData(join: Boolean, viewState: Boolean, setState: Boolean, manage: Boolean)

  case class ActivityData(activityType: String, activityId: String, ephemeral: Boolean, created: Long)

  def toActivityData(activity: Activity): ActivityData = {
    val Activity(ActivityId(activityType, activityId), ephemeral, created) = activity
    ActivityData(activityType, activityId, ephemeral, created.toEpochMilli)
  }

  def stringsToPermissions(permissions: Set[String]): ActivityPermissionsRestData = {
    ActivityPermissionsRestData(
      permissions.contains(ActivityPermission.Constants.Join),
      permissions.contains(ActivityPermission.Constants.ViewState),
      permissions.contains(ActivityPermission.Constants.SetState),
      permissions.contains(ActivityPermission.Constants.Manage)
    )
  }

  def toPermissionStrings(data: ActivityPermissionsRestData): Set[String] = {
    val result = scala.collection.mutable.Set[String]()
    if (data.join) {
      result += ActivityPermission.Constants.Join
    }

    if (data.manage) {
      result += ActivityPermission.Constants.Manage
    }

    if (data.setState) {
      result += ActivityPermission.Constants.SetState
    }

    if (data.viewState) {
      result += ActivityPermission.Constants.ViewState
    }

    result.toSet
  }
}
