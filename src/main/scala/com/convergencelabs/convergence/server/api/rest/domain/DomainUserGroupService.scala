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

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{_enhanceRouteWithConcatenation, _segmentStringToPathMatcher, _string2NR, as, complete, delete, entity, get, parameters, path, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.datastore.domain.UserGroupStoreActor._
import com.convergencelabs.convergence.server.datastore.domain.{UserGroup, UserGroupInfo, UserGroupSummary}
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

object DomainUserGroupService {

  case class UserGroupData(id: String, description: String, members: Set[String])

  case class UserGroupSummaryData(id: String, description: String, members: Int)

  case class UserGroupInfoData(id: String, description: String)

}

class DomainUserGroupService(private[this] val executionContext: ExecutionContext,
                             private[this] val timeout: Timeout,
                             private[this] val domainRestActor: ActorRef)
  extends AbstractDomainRestService(executionContext, timeout) {

  import DomainUserGroupService._

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("groups") {
      pathEnd {
        get {
          parameters("type".?, "filter".?, "offset".as[Int].?, "limit".as[Int].?) { (responseType, filter, offset, limit) =>
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

  private[this] def getUserGroups(domain: DomainId, resultType: Option[String], filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    resultType.getOrElse("all") match {
      case "all" =>
        val message = DomainRestMessage(domain, GetUserGroupsRequest(filter, offset, limit))
        (domainRestActor ? message)
          .mapTo[GetUserGroupsResponse]
          .map(_.userGroups)
          .map(groups => okResponse(groups.map(groupToUserGroupData)))
      case "summary" =>
        val message = DomainRestMessage(domain, GetUserGroupSummariesRequest(None, limit, offset))
        (domainRestActor ? message).mapTo[GetUserGroupSummariesResponse]
          .map(_.summaries)
          .map(groups =>
            okResponse(groups.map { c =>
              val UserGroupSummary(id, desc, count) = c
              UserGroupSummaryData(id, desc, count)
            }))
      case t =>
        Future.successful((StatusCodes.BadRequest, ErrorResponse("invalid_type", Some(s"Invalid type: $t"))))
    }
  }

  private[this] def getUserGroup(domain: DomainId, groupId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetUserGroupRequest(groupId))
    (domainRestActor ? message)
      .mapTo[GetUserGroupResponse]
      .map(_.userGroup)
      .map {
        case Some(group) => okResponse(groupToUserGroupData(group))
        case None => notFoundResponse()
      }
  }

  private[this] def getUserGroupMembers(domain: DomainId, groupId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetUserGroupRequest(groupId))
    (domainRestActor ? message).mapTo[GetUserGroupResponse].map(_.userGroup) map {
      case Some(UserGroup(_, _, members)) => okResponse(members.map(_.username))
      case None => notFoundResponse()
    }
  }

  private[this] def getUserGroupInfo(domain: DomainId, groupId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetUserGroupInfoRequest(groupId))
    (domainRestActor ? message).mapTo[GetUserGroupInfoResponse].map(_.groupInfo) map {
      case Some(UserGroupInfo(id, description)) =>
        okResponse(UserGroupInfoData(id, description))
      case None =>
        notFoundResponse()
    }
  }

  private[this] def updateUserGroupInfo(domain: DomainId, groupId: String, infoData: UserGroupInfoData): Future[RestResponse] = {
    val UserGroupInfoData(id, description) = infoData
    val info = UserGroupInfo(id, description)
    val message = DomainRestMessage(domain, UpdateUserGroupInfoRequest(groupId, info))
    (domainRestActor ? message).mapTo[Unit] map (_ => OkResponse)
  }

  private[this] def addUserToGroup(domain: DomainId, groupId: String, username: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, AddUserToGroupRequest(groupId, DomainUserId.normal(username)))
    (domainRestActor ? message).mapTo[Unit] map (_ => OkResponse)
  }

  private[this] def removeUserFromGroup(domain: DomainId, groupId: String, username: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, RemoveUserFromGroupRequest(groupId, DomainUserId.normal(username)))
    (domainRestActor ? message).mapTo[Unit] map (_ => OkResponse)
  }

  private[this] def createUserGroup(domain: DomainId, groupData: UserGroupData): Future[RestResponse] = {
    val group = this.groupDataToUserGroup(groupData)
    val message = DomainRestMessage(domain, CreateUserGroupRequest(group))
    (domainRestActor ? message) map { _ => CreatedResponse }
  }

  private[this] def updateUserGroup(domain: DomainId, groupId: String, groupData: UserGroupData): Future[RestResponse] = {
    val group = this.groupDataToUserGroup(groupData)
    val message = DomainRestMessage(domain, UpdateUserGroupRequest(groupId, group))
    (domainRestActor ? message) map (_ => OkResponse)
  }

  private[this] def deleteUserGroup(domain: DomainId, groupId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, DeleteUserGroupRequest(groupId))
    (domainRestActor ? message) map (_ => DeletedResponse)
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
}
