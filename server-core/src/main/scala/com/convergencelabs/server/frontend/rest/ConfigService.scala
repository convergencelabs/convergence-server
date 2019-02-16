package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor.UpdateConvergenceUserRequest
import com.convergencelabs.server.datastore.convergence.UserStore.User

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives._string2NR
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.model._
import akka.util.Timeout
import grizzled.slf4j.Logging
import com.convergencelabs.server.security.AuthorizationProfile
import com.convergencelabs.server.datastore.convergence.ConfigStoreActor.GetConfigs
import com.convergencelabs.server.datastore.convergence.ConfigStoreActor.SetConfigs

object ConfigService {
  case class BearerTokenResponse(token: String)
  case class CovergenceUserProfile(username: String, email: String, firstName: String, lastName: String, displayName: String)
  case class UpdateProfileRequest(email: String, firstName: String, lastName: String, displayName: String)
  case class PasswordSetRequest(password: String)
}

class ConfigService(
  private[this] val executionContext: ExecutionContext,
  private[this] val configActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
  extends JsonSupport
  with Logging {

  import ConfigService._
  import akka.pattern.ask

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { authProfile: AuthorizationProfile =>
    pathPrefix("config") {
      pathEnd {
        get {
          parameters(_string2NR("keys").*) { keys =>
            complete(getAllConfigs(authProfile, keys))
          }
        } ~ post {
          entity(as[Map[String, Any]]) { configs =>
            complete(setConfigs(authProfile, configs))
          }
        }
      } ~ path("app") {
        complete(getAppConfigs(authProfile))
      }
    }
  }

  def getAllConfigs(authProfile: AuthorizationProfile, keys: Iterable[String]): Future[RestResponse] = {
    val keyFilter = keys.toList match {
      case Nil => None
      case k => Some(k)
    }
    val message = GetConfigs(keyFilter)
    (configActor ? message).mapTo[Map[String, Any]].map(okResponse(_))
  }

  def getAppConfigs(authProfile: AuthorizationProfile): Future[RestResponse] = {
    // FIXME request specific keys
    val message = GetConfigs(None)
    (configActor ? message).mapTo[Map[String, Any]].map(okResponse(_))
  }

  def setConfigs(authProfile: AuthorizationProfile, configs: Map[String, Any]): Future[RestResponse] = {
    val message = SetConfigs(configs)
    (configActor ? message).mapTo[Unit].map(_ => OkResponse)
  }
}
