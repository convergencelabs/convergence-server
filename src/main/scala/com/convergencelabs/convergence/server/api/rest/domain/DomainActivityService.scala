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
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _string2NR, as, complete, delete, entity, get, parameters, pathEnd, pathPrefix, put}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.api.rest.domain.DomainActivityService._
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityActor.{CreateRequest, CreateResponse, DeleteRequest, DeleteResponse}
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityServiceActor.{GetActivitiesRequest, GetActivitiesResponse, GetActivityRequest, GetActivityResponse}
import com.convergencelabs.convergence.server.backend.services.domain.activity.{ActivityActor, ActivityServiceActor}
import com.convergencelabs.convergence.server.backend.services.domain.permissions.{AllPermissions, SetGroupPermissions, SetPermissions, SetUserPermissions}
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
        }
      } ~ pathPrefix(Segment / Segment) { (activityType, activityId) =>
        val id = ActivityId(activityType, activityId)
        pathEnd {
          get {
            complete(getActivity(domainId, id))
          } ~ delete {
            complete(deleteActivity(domainId, id))
          } ~ put {
            entity(as[CreateActivityData]) { activityData =>
              complete(createActivity(domainId, id, activityData))
            }
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

  private[this] def createActivity(domainId: DomainId, activityId: ActivityId, data: CreateActivityData): Future[RestResponse] = {
    val CreateActivityData(world, user, group) = data

    val userPermissions = user.map { case (userId, permissions) =>
      DomainUserId.normal(userId) -> permissions
    }

    val allPermissions = AllPermissions(world, userPermissions, group)

    activityShardRegion.ask[CreateResponse](r => CreateRequest(domainId, activityId, None, allPermissions, r))
      .map(_.response.fold(
        {
          case ActivityActor.AlreadyExists() =>
            conflictsResponse("activityId", activityId.id)
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
          okResponse(PagedRestResponse(data, pagedData.offset, pagedData.count))
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
          okResponse(toActivityData(activity))
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
          val response = AllPermissionsRestData(permissions.world, permissions.user, permissions.group)
          okResponse(response)
        }
      ))
  }

  private[this] def setPermissions(domainId: DomainId,
                                   activityId: ActivityId,
                                   permissions: SetPermissionsRestData): Future[RestResponse] = {
    val SetPermissionsRestData(world, user, group) = permissions
    val userPermissions = user.map { p =>
      SetUserPermissions(p.permissions, p.replace)
    }
    val groupPermissions = group.map { p =>
      SetGroupPermissions(p.permissions, p.replace)
    }

    val setPermissions = SetPermissions(world.map(_.permissions), userPermissions, groupPermissions)

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
  case class CreateActivityData(worldPermissions: Set[String],
                                userPermissions: Map[String, Set[String]],
                                groupPermissions: Map[String, Set[String]])

  final case class AllPermissionsRestData(worldPermissions: Set[String],
                                          userPermissions: Map[DomainUserId, Set[String]],
                                          groupPermissions: Map[String, Set[String]])

  final case class SetPermissionsRestData(worldPermissions: Option[SetWorldPermissionsData],
                                          userPermissions: Option[SetUserPermissionsData],
                                          groupPermissions: Option[SetGroupPermissionsData])

  final case class SetWorldPermissionsData(permissions: Set[String])

  final case class SetUserPermissionsData(permissions: Map[DomainUserId, Set[String]], replace: Boolean)

  final case class SetGroupPermissionsData(permissions: Map[String, Set[String]], replace: Boolean)

  final case class ActivityData(activityType: String, activityId: String, ephemeral: Boolean, created: Long)

  def toActivityData(activity: Activity): ActivityData = {
    val Activity(ActivityId(activityType, activityId), ephemeral, created) = activity
    ActivityData(activityType, activityId, ephemeral, created.toEpochMilli)
  }
}
