package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage

import DomainUserService.GetUsersRestResponse
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Directives.authorizeAsync
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.frontend.rest.DomainConfigService.AnonymousAuthPut
import com.convergencelabs.server.frontend.rest.DomainConfigService.ModelSnapshotPolicyData
import com.convergencelabs.server.frontend.rest.DomainConfigService.ModelSnapshotPolicyResponse
import com.convergencelabs.server.datastore.ConfigStoreActor.GetAnonymousAuth
import com.convergencelabs.server.frontend.rest.DomainConfigService.AnonymousAuthResponse
import com.convergencelabs.server.datastore.ConfigStoreActor.SetAnonymousAuth
import com.convergencelabs.server.domain.AuthorizationActor.ConvergenceAuthorizedRequest
import scala.util.Try
import com.convergencelabs.server.domain.ModelSnapshotConfig
import java.time.Duration
import com.convergencelabs.server.datastore.ConfigStoreActor.SetModelSnapshotPolicy
import com.convergencelabs.server.datastore.ConfigStoreActor.GetModelSnapshotPolicy

object DomainConfigService {
  case class AnonymousAuthPut(enabled: Boolean)
  case class AnonymousAuthResponse(enabled: Boolean) extends AbstractSuccessResponse
  case class ModelSnapshotPolicyResponse(policy: ModelSnapshotPolicyData) extends AbstractSuccessResponse
  case class ModelSnapshotPolicyData(
    snapshotsEnabled: Boolean,
    triggerByVersion: Boolean,
    maximumVersionInterval: Long,
    limitByVersion: Boolean,
    minimumVersionInterval: Long,
    triggerByTime: Boolean,
    maximumTimeInterval: Long,
    limitByTime: Boolean,
    minimumTimeInterval: Long)
}

class DomainConfigService(
  private[this] val executionContext: ExecutionContext,
  private[this] val authorizationActor: ActorRef,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("config") {
      path("anonymousAuth") {
        get {
          authorizeAsync(canAccessDomain(domain, username)) {
            complete(getAnonymousAuthEnabled(domain))
          }
        } ~ put {
          entity(as[AnonymousAuthPut]) { request =>
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(setAnonymousAuthEnabled(domain, request))
            }
          }
        }
      } ~
        path("modelSnapshotPolicy") {
          get {
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(getModelSnapshotPolicy(domain))
            }
          } ~ put {
            entity(as[ModelSnapshotPolicyData]) { policyData =>
              authorizeAsync(canAccessDomain(domain, username)) {
                complete(setModelSnapshotPolicy(domain, policyData))
              }
            }
          }
        }
    }
  }

  def getAnonymousAuthEnabled(domain: DomainFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, GetAnonymousAuth)
    (domainRestActor ? message).mapTo[Boolean] map
      (enabled => (StatusCodes.OK, AnonymousAuthResponse(enabled)))
  }

  def setAnonymousAuthEnabled(domain: DomainFqn, request: AnonymousAuthPut): Future[RestResponse] = {
    val message = DomainMessage(domain, SetAnonymousAuth(request.enabled))
    (domainRestActor ? message) map (_ => OkResponse)
  }

  def getModelSnapshotPolicy(domain: DomainFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModelSnapshotPolicy)
    (domainRestActor ? message).mapTo[ModelSnapshotConfig] map { config =>
      val ModelSnapshotConfig(
        snapshotsEnabled,
        triggerByVersion,
        limitByVersion,
        minimumVersionInterval,
        maximumVersionInterval,
        triggerByTime,
        limitByTime,
        minimumTimeInterval,
        maximumTimeInterval) = config;
      (StatusCodes.OK, ModelSnapshotPolicyResponse(
        ModelSnapshotPolicyData(
          snapshotsEnabled,
          triggerByVersion,
          maximumVersionInterval,
          limitByVersion,
          minimumVersionInterval,
          triggerByTime,
          maximumTimeInterval.toMillis,
          limitByTime,
          minimumTimeInterval.toMillis)))
    }
  }

  def setModelSnapshotPolicy(domain: DomainFqn, policyData: ModelSnapshotPolicyData): Future[RestResponse] = {
    val ModelSnapshotPolicyData(
        snapshotsEnabled,
        triggerByVersion,
        maximumVersionInterval,
        limitByVersion,
        minimumVersionInterval,
        triggerByTime,
        maximumTimeInterval,
        limitByTime,
        minimumTimeInterval
        ) = policyData;
    
      val policy = 
        ModelSnapshotConfig(
          snapshotsEnabled,
          triggerByVersion,
          limitByVersion,
          minimumVersionInterval,
          maximumVersionInterval,
          triggerByTime,
          limitByTime,
          Duration.ofMillis(minimumTimeInterval),
          Duration.ofMillis(maximumTimeInterval)
          )
  
    val message = DomainMessage(domain, SetModelSnapshotPolicy(policy))
    (domainRestActor ? message) map (_ => OkResponse)
  }

  // Permission Checks

  def canAccessDomain(domainFqn: DomainFqn, username: String): Future[Boolean] = {
    (authorizationActor ? ConvergenceAuthorizedRequest(username, domainFqn, Set("domain-access"))).mapTo[Try[Boolean]].map(_.get)
  }
}
