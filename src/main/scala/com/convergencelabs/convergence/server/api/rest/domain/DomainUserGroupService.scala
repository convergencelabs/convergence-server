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
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.group.{UserGroup, UserGroupInfo, UserGroupSummary}
import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.backend.services.domain.group.UserGroupStoreActor._
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}

import scala.concurrent.{ExecutionContext, Future}

object DomainUserGroupService {

  case class UserGroupData(id: String, description: String, members: Set[String])

  case class UserGroupSummaryData(id: String, description: String, members: Int)

  case class UserGroupInfoData(id: String, description: String)

}

class DomainUserGroupService(domainRestActor: ActorRef[DomainRestActor.Message],
                             scheduler: Scheduler,
                             executionContext: ExecutionContext,
                             timeout: Timeout)
  extends AbstractDomainRestService(scheduler, executionContext, timeout) {

  import DomainUserGroupService._

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("groups") {
      pathEnd {
        get {
          parameters("type".?, "filter".?, "offset".as[Long].?, "limit".as[Long].?) { (responseType, filter, offset, limit) =>
            complete(getUserGroups(domain, responseType, filter, offset, limit))
          }
        } ~ post {
          entity(as[UserGroupData]) { group =>
            complete(createUserGroup(domain, group))
          }
        }
      } ~ pathPrefix(Segment) { groupId =>
        pathEnd {
          get {
            complete(getUserGroup(domain, groupId))
          } ~ delete {
            complete(deleteUserGroup(domain, groupId))
          } ~ put {
            entity(as[UserGroupData]) { updateData =>
              complete(updateUserGroup(domain, groupId, updateData))
            }
          }
        } ~ path("info") {
          get {
            complete(getUserGroupInfo(domain, groupId))
          } ~ put {
            entity(as[UserGroupInfoData]) { updateData =>
              complete(updateUserGroupInfo(domain, groupId, updateData))
            }
          }
        } ~ pathPrefix("members") {
          pathEnd {
            get {
              complete(getUserGroupMembers(domain, groupId))
            }
          } ~ path(Segment) { groupUser =>
            put {
              complete(addUserToGroup(domain, groupId, groupUser))
            } ~ delete {
              complete(removeUserFromGroup(domain, groupId, groupUser))
            }
          }
        }
      }
    }
  }

  private[this] def getUserGroups(domain: DomainId, resultType: Option[String], filter: Option[String], offset: Option[Long], limit: Option[Long]): Future[RestResponse] = {
    resultType.getOrElse("all") match {
      case "all" =>
        domainRestActor
          .ask[GetUserGroupsResponse](r =>
            DomainRestMessage(domain, GetUserGroupsRequest(filter, QueryOffset(offset), QueryLimit(limit), r)))
          .map(_.userGroups.fold(
            {
              case UnknownError() =>
                InternalServerError
            },
            userGroups => okResponse(userGroups.map(groupToUserGroupData))
          ))
      case "summary" =>
        // FIXME what about the filter?
        domainRestActor
          .ask[GetUserGroupSummariesResponse](r =>
            DomainRestMessage(domain, GetUserGroupSummariesRequest(None, QueryOffset(offset), QueryLimit(limit), r)))
          .map(_.summaries.fold(
            {
              case UnknownError() =>
                InternalServerError
            },
            summaries => okResponse(summaries.map { c =>
              val UserGroupSummary(id, desc, count) = c
              UserGroupSummaryData(id, desc, count)
            })
          ))
      case t =>
        Future.successful(badRequest(s"Invalid type: $t"))
    }
  }

  private[this] def getUserGroup(domain: DomainId, groupId: String): Future[RestResponse] = {
    domainRestActor
      .ask[GetUserGroupResponse](r => DomainRestMessage(domain, GetUserGroupRequest(groupId, r)))
      .map(_.userGroup.fold(
        {
          case GroupNotFoundError() =>
            groupNotFound()
          case UnknownError() =>
            InternalServerError
        },
        userGroup => okResponse(groupToUserGroupData(userGroup))
      ))
  }

  private[this] def getUserGroupMembers(domain: DomainId, groupId: String): Future[RestResponse] = {
    domainRestActor
      .ask[GetUserGroupResponse](r => DomainRestMessage(domain, GetUserGroupRequest(groupId, r)))
      .map(_.userGroup.fold(
        {
          case GroupNotFoundError() =>
            groupNotFound()
          case UnknownError() =>
            InternalServerError
        },
        userGroup => okResponse(userGroup.members.map(_.username))
      ))
  }

  private[this] def getUserGroupInfo(domain: DomainId, groupId: String): Future[RestResponse] = {
    domainRestActor
      .ask[GetUserGroupInfoResponse](r => DomainRestMessage(domain, GetUserGroupInfoRequest(groupId, r)))
      .map(_.groupInfo.fold(
        {
          case GroupNotFoundError() =>
            groupNotFound()
          case UnknownError() =>
            InternalServerError
        },
        {
          case UserGroupInfo(id, description) =>
            okResponse(UserGroupInfoData(id, description))
        }
      ))
  }

  private[this] def updateUserGroupInfo(domain: DomainId, groupId: String, infoData: UserGroupInfoData): Future[RestResponse] = {
    val UserGroupInfoData(id, description) = infoData
    val info = UserGroupInfo(id, description)
    domainRestActor
      .ask[UpdateUserGroupInfoResponse](
        r => DomainRestMessage(domain, UpdateUserGroupInfoRequest(groupId, info, r)))
      .map(_.response.fold(
        {
          case GroupNotFoundError() =>
            groupNotFound()
          case UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def addUserToGroup(domain: DomainId, groupId: String, username: String): Future[RestResponse] = {
    domainRestActor
      .ask[AddUserToGroupResponse](
        r => DomainRestMessage(domain, AddUserToGroupRequest(groupId, DomainUserId.normal(username), r)))
      .map(_.response.fold(
        {
          case UserNotFoundError() =>
            userNotFound()
          case GroupNotFoundError() =>
            groupNotFound()
          case UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def removeUserFromGroup(domain: DomainId, groupId: String, username: String): Future[RestResponse] = {
    domainRestActor
      .ask[RemoveUserFromGroupResponse](
        r => DomainRestMessage(domain, RemoveUserFromGroupRequest(groupId, DomainUserId.normal(username), r)))
      .map(_.response.fold(
        {
          case UserNotFoundError() =>
            userNotFound()
          case GroupNotFoundError() =>
            groupNotFound()
          case UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def createUserGroup(domain: DomainId, groupData: UserGroupData): Future[RestResponse] = {
    val group = this.groupDataToUserGroup(groupData)
    domainRestActor
      .ask[CreateUserGroupResponse](r => DomainRestMessage(domain, CreateUserGroupRequest(group, r)))
      .map(_.response.fold(
        {
          case GroupAlreadyExistsError() =>
            conflictsResponse("id", groupData.id)
          case UnknownError() =>
            InternalServerError
        },
        _ => CreatedResponse
      ))
  }

  private[this] def updateUserGroup(domain: DomainId, groupId: String, groupData: UserGroupData): Future[RestResponse] = {
    val group = this.groupDataToUserGroup(groupData)
    domainRestActor
      .ask[UpdateUserGroupResponse](r => DomainRestMessage(domain, UpdateUserGroupRequest(groupId, group, r)))
      .map(_.response.fold(
        {
          case GroupNotFoundError() =>
            groupNotFound()
          case UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def deleteUserGroup(domain: DomainId, groupId: String): Future[RestResponse] = {
    domainRestActor
      .ask[DeleteUserGroupResponse](r => DomainRestMessage(domain, DeleteUserGroupRequest(groupId, r)))
      .map(_.response.fold(
        {
          case GroupNotFoundError() =>
            groupNotFound()
          case UnknownError() =>
            InternalServerError
        },
        _ => DeletedResponse
      ))
  }

  private[this] def groupDataToUserGroup(groupData: UserGroupData): UserGroup = {
    // FIXME is this what we want? Assume normal user?
    val UserGroupData(id, description, members) = groupData
    UserGroup(id, description, members.map(DomainUserId(DomainUserType.Normal, _)))
  }

  private[this] def groupToUserGroupData(group: UserGroup): UserGroupData = {
    val UserGroup(id, description, members) = group
    UserGroupData(id, description, members.map(_.username))
  }

  private[this] def userNotFound(): RestResponse = notFoundResponse("The specified user does not exist.")

  private[this] def groupNotFound(): RestResponse = notFoundResponse("The specified group does not exist.")
}
