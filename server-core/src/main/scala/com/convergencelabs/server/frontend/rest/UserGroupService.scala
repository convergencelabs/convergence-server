package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import com.convergencelabs.server.datastore.UserGroupStoreActor.AddUserToGroup
import com.convergencelabs.server.datastore.UserGroupStoreActor.CreateUserGroup
import com.convergencelabs.server.datastore.UserGroupStoreActor.DeleteUserGroup
import com.convergencelabs.server.datastore.UserGroupStoreActor.GetUserGroup
import com.convergencelabs.server.datastore.UserGroupStoreActor.GetUserGroupSummaries
import com.convergencelabs.server.datastore.UserGroupStoreActor.GetUserGroups
import com.convergencelabs.server.datastore.UserGroupStoreActor.RemoveUserFromGroup
import com.convergencelabs.server.datastore.UserGroupStoreActor.UpdateUserGroup
import com.convergencelabs.server.datastore.domain.UserGroup
import com.convergencelabs.server.datastore.domain.UserGroupSummary
import com.convergencelabs.server.domain.AuthorizationActor.ConvergenceAuthorizedRequest
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage
import com.convergencelabs.server.frontend.rest.UserGroupService.UserGroupData

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives._string2NR
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.authorizeAsync
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout

object UserGroupService {
  case class GetUserGroupsResponse(groups: List[UserGroupData]) extends AbstractSuccessResponse
  case class GetUserGroupResponse(group: UserGroupData) extends AbstractSuccessResponse
  case class GetUserGroupMembersResponse(members: Set[String]) extends AbstractSuccessResponse
  case class GetUserGroupSummaryResponse(groups: List[UserGroupSummaryData]) extends AbstractSuccessResponse
  case class GetUserGroupSummariesResponse(groups: Set[UserGroupSummaryData]) extends AbstractSuccessResponse

  case class UserGroupData(id: String, description: String, members: Set[String])
  case class UserGroupSummaryData(id: String, description: String, memberCount: Int)
}

class UserGroupService(
  private[this] val executionContext: ExecutionContext,
  private[this] val authorizationActor: ActorRef,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  import UserGroupService._

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("groups") {
      pathEnd {
        get {
          authorizeAsync(canAccessDomain(domain, username)) {
            complete(getUserGroups(domain))
          }
        } ~ post {
          entity(as[UserGroupData]) { group =>
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(createUserGroup(domain, group))
            }
          }
        }
      } ~ pathPrefix(Segment) { groupId =>
        pathEnd {
          get {
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(getUserGroup(domain, groupId))
            }
          } ~ delete {
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(deleteUserGroup(domain, groupId))
            }
          } ~ put {
            entity(as[UserGroupData]) { updateData =>
              authorizeAsync(canAccessDomain(domain, username)) {
                complete(updateUserGroup(domain, groupId, updateData))
              }
            }
          }
        } ~ pathPrefix("members") {
          pathEnd {
            get {
              authorizeAsync(canAccessDomain(domain, username)) {
                complete(getUserGroupMembers(domain, groupId))
              }
            }
          } ~ pathPrefix(Segment) { groupUser =>
            pathEnd {
              put {
                authorizeAsync(canAccessDomain(domain, username)) {
                  complete(addUserToGroup(domain, groupId, groupUser))
                }
              } ~ delete {
                authorizeAsync(canAccessDomain(domain, username)) {
                  complete(removeUserFromGroup(domain, groupId, groupUser))
                }
              }
            }
          }
        } ~ pathPrefix("summary") {
          pathEnd {
            get {
              parameters("limit".as[Int].?, "offset".as[Int].?) { (limit, offset) =>
                authorizeAsync(canAccessDomain(domain, username)) {
                  complete(getUserGroupSummaries(domain, limit, offset))
                }
              }
            }
          }
        }
      }
    }
  }

  def getUserGroups(domain: DomainFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, GetUserGroups(None, None, None))
    (domainRestActor ? message).mapTo[List[UserGroup]] map (groups =>
      (StatusCodes.OK, GetUserGroupsResponse(groups.map(groupToUserGroupData(_)))))
  }

  def getUserGroup(domain: DomainFqn, groupId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetUserGroup(groupId))
    (domainRestActor ? message).mapTo[Option[UserGroup]] map {
      case Some(group) => (StatusCodes.OK, GetUserGroupResponse(groupToUserGroupData(group)))
      case None => NotFoundError
    }
  }
  
  def getUserGroupMembers(domain: DomainFqn, groupId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetUserGroup(groupId))
    (domainRestActor ? message).mapTo[Option[UserGroup]] map {
      case Some(UserGroup(_, _, members)) => (StatusCodes.OK, GetUserGroupMembersResponse(members))
      case None => NotFoundError
    }
  }

  def addUserToGroup(domain: DomainFqn, groupId: String, username: String): Future[RestResponse] = {
    val message = DomainMessage(domain, AddUserToGroup(groupId, username))
    (domainRestActor ? message).mapTo[Unit] map ( _ => OkResponse)
  }
  
  def removeUserFromGroup(domain: DomainFqn, groupId: String, username: String): Future[RestResponse] = {
    val message = DomainMessage(domain, RemoveUserFromGroup(groupId, username))
    (domainRestActor ? message).mapTo[Unit] map ( _ => OkResponse)
  }

  def createUserGroup(domain: DomainFqn, groupData: UserGroupData): Future[RestResponse] = {
    val group = this.groupDataToUserGroup(groupData)
    val message = DomainMessage(domain, CreateUserGroup(group))
    (domainRestActor ? message) map { _ => CreateRestResponse }
  }

  def updateUserGroup(domain: DomainFqn, groupId: String, groupData: UserGroupData): Future[RestResponse] = {
    val group = this.groupDataToUserGroup(groupData)
    val message = DomainMessage(domain, UpdateUserGroup(groupId, group))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def deleteUserGroup(domain: DomainFqn, groupId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, DeleteUserGroup(groupId))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getUserGroupSummaries(domain: DomainFqn, limit: Option[Int], offset: Option[Int]): Future[RestResponse] = {
    val message = DomainMessage(domain, GetUserGroupSummaries(None, limit, offset))
    (domainRestActor ? message).mapTo[List[UserGroupSummary]] map (groups =>
      (StatusCodes.OK, GetUserGroupSummaryResponse(groups.map { c =>
        val UserGroupSummary(id, desc, count) = c
        UserGroupSummaryData(id, desc, count)
      })))
  }

  // Permission Checks

  def canAccessDomain(domainFqn: DomainFqn, username: String): Future[Boolean] = {
    (authorizationActor ? ConvergenceAuthorizedRequest(username, domainFqn, Set("domain-access"))).mapTo[Try[Boolean]].map(_.get)
  }

  def groupDataToUserGroup(groupData: UserGroupData): UserGroup = {
    val UserGroupData(id, description, members) = groupData
    UserGroup(id, description, members)
  }

  def groupToUserGroupData(group: UserGroup): UserGroupData = {
    val UserGroup(id, description, members) = group
    UserGroupData(id, description, members)
  }
}
