package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import com.convergencelabs.server.datastore.domain.UserGroup
import com.convergencelabs.server.datastore.domain.UserGroupInfo
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor.AddUserToGroup
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor.CreateUserGroup
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor.DeleteUserGroup
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor.GetUserGroup
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor.GetUserGroupInfo
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor.GetUserGroupSummaries
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor.GetUserGroups
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor.RemoveUserFromGroup
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor.UpdateUserGroup
import com.convergencelabs.server.datastore.domain.UserGroupStoreActor.UpdateUserGroupInfo
import com.convergencelabs.server.datastore.domain.UserGroupSummary
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.frontend.rest.DomainUserGroupService.UserGroupData

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
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
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.security.AuthorizationProfile

object DomainUserGroupService {
  case class GetUserGroupsResponse(groups: List[UserGroupData])
  case class GetUserGroupResponse(group: UserGroupData)
  case class GetUserGroupInfoResponse(group: UserGroupInfoData)
  case class GetUserGroupMembersResponse(members: Set[String])
  case class GetUserGroupSummaryResponse(groups: List[UserGroupSummaryData])
  case class GetUserGroupSummariesResponse(groups: Set[UserGroupSummaryData])

  case class UserGroupData(id: String, description: String, members: Set[String])
  case class UserGroupSummaryData(id: String, description: String, members: Int)
  case class UserGroupInfoData(id: String, description: String)
}

class DomainUserGroupService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val domainRestActor: ActorRef)
    extends DomainRestService(executionContext, timeout) {

  import DomainUserGroupService._
  import akka.pattern.ask

  def route(authProfile: AuthorizationProfile, domain: DomainFqn): Route = {
    pathPrefix("groups") {
      pathEnd {
        get {
          parameters("type".?, "filter".?, "offset".as[Int].?, "limit".as[Int].?) { (responseType, filter, offset, limit) =>
            authorize(canAccessDomain(domain, authProfile)) {
              complete(getUserGroups(domain, responseType, filter, offset, limit))
            }
          }
        } ~ post {
          entity(as[UserGroupData]) { group =>
            authorize(canAccessDomain(domain, authProfile)) {
              complete(createUserGroup(domain, group))
            }
          }
        }
      } ~ pathPrefix(Segment) { groupId =>
        pathEnd {
          get {
            authorize(canAccessDomain(domain, authProfile)) {
              complete(getUserGroup(domain, groupId))
            }
          } ~ delete {
            authorize(canAccessDomain(domain, authProfile)) {
              complete(deleteUserGroup(domain, groupId))
            }
          } ~ put {
            entity(as[UserGroupData]) { updateData =>
              authorize(canAccessDomain(domain, authProfile)) {
                complete(updateUserGroup(domain, groupId, updateData))
              }
            }
          }
        } ~ path("info") {
          get {
            authorize(canAccessDomain(domain, authProfile)) {
              complete(getUserGroupInfo(domain, groupId))
            }
          } ~ put {
            entity(as[UserGroupInfoData]) { updateData =>
              authorize(canAccessDomain(domain, authProfile)) {
                complete(updateUserGroupInfo(domain, groupId, updateData))
              }
            }
          }
        } ~ pathPrefix("members") {
          pathEnd {
            get {
              authorize(canAccessDomain(domain, authProfile)) {
                complete(getUserGroupMembers(domain, groupId))
              }
            }
          } ~ path(Segment) { groupUser =>
            put {
              authorize(canAccessDomain(domain, authProfile)) {
                complete(addUserToGroup(domain, groupId, groupUser))
              }
            } ~ delete {
              authorize(canAccessDomain(domain, authProfile)) {
                complete(removeUserFromGroup(domain, groupId, groupUser))
              }
            }
          }
        }
      }
    }
  }

  def getUserGroups(domain: DomainFqn, resultType: Option[String], filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    resultType.getOrElse("all") match {
      case "all" =>
        val message = DomainRestMessage(domain, GetUserGroups(filter, offset, limit))
        (domainRestActor ? message).mapTo[List[UserGroup]] map (groups =>
          okResponse(GetUserGroupsResponse(groups.map(groupToUserGroupData(_)))))
      case "summary" =>
        val message = DomainRestMessage(domain, GetUserGroupSummaries(None, limit, offset))
        (domainRestActor ? message).mapTo[List[UserGroupSummary]] map (groups =>
          okResponse(GetUserGroupSummaryResponse(groups.map { c =>
            val UserGroupSummary(id, desc, count) = c
            UserGroupSummaryData(id, desc, count)
          })))
      case t =>
        Future.successful((StatusCodes.BadRequest, ErrorResponse("invalid_type", Some(s"Invalid type: $t"))))
    }
  }

  def getUserGroup(domain: DomainFqn, groupId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetUserGroup(groupId))
    (domainRestActor ? message).mapTo[Option[UserGroup]] map {
      case Some(group) => okResponse(GetUserGroupResponse(groupToUserGroupData(group)))
      case None => notFoundResponse()
    }
  }

  def getUserGroupMembers(domain: DomainFqn, groupId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetUserGroup(groupId))
    (domainRestActor ? message).mapTo[Option[UserGroup]] map {
      case Some(UserGroup(_, _, members)) => okResponse(GetUserGroupMembersResponse(members.map(_.username)))
      case None => notFoundResponse()
    }
  }

  def getUserGroupInfo(domain: DomainFqn, groupId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetUserGroupInfo(groupId))
    (domainRestActor ? message).mapTo[Option[UserGroupInfo]] map {
      case Some(UserGroupInfo(id, description)) =>
        okResponse(GetUserGroupInfoResponse(UserGroupInfoData(id, description)))
      case None => 
        notFoundResponse()
    }
  }
  
  def updateUserGroupInfo(domain: DomainFqn, groupId: String, infoData: UserGroupInfoData): Future[RestResponse] = {
    val UserGroupInfoData(id, description) = infoData
    val info = UserGroupInfo(id, description)
    val message = DomainRestMessage(domain, UpdateUserGroupInfo(groupId, info))
    (domainRestActor ? message).mapTo[Unit] map { _ => OkResponse }
  }

  def addUserToGroup(domain: DomainFqn, groupId: String, username: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, AddUserToGroup(groupId, DomainUserId.normal(username)))
    (domainRestActor ? message).mapTo[Unit] map (_ => OkResponse)
  }

  def removeUserFromGroup(domain: DomainFqn, groupId: String, username: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, RemoveUserFromGroup(groupId, DomainUserId.normal(username)))
    (domainRestActor ? message).mapTo[Unit] map (_ => OkResponse)
  }

  def createUserGroup(domain: DomainFqn, groupData: UserGroupData): Future[RestResponse] = {
    val group = this.groupDataToUserGroup(groupData)
    val message = DomainRestMessage(domain, CreateUserGroup(group))
    (domainRestActor ? message) map { _ => CreatedResponse }
  }

  def updateUserGroup(domain: DomainFqn, groupId: String, groupData: UserGroupData): Future[RestResponse] = {
    val group = this.groupDataToUserGroup(groupData)
    val message = DomainRestMessage(domain, UpdateUserGroup(groupId, group))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def deleteUserGroup(domain: DomainFqn, groupId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, DeleteUserGroup(groupId))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def groupDataToUserGroup(groupData: UserGroupData): UserGroup = {
    // FIXME is this what we want? Assume normal user?
    val UserGroupData(id, description, members) = groupData
    UserGroup(id, description, members.map(DomainUserId(DomainUserType.Normal, _)))
  }

  def groupToUserGroupData(group: UserGroup): UserGroupData = {
    val UserGroup(id, description, members) = group
    UserGroupData(id, description, members.map(_.username))
  }
}
