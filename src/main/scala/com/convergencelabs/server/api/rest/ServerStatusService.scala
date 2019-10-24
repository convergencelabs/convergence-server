package com.convergencelabs.server.api.rest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives.{_segmentStringToPathMatcher, complete, get, pathEnd, pathPrefix}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.datastore.convergence.ServerStatusActor.{GetStatusRequest, ServerStatusResponse}
import com.convergencelabs.server.security.AuthorizationProfile
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

object ServerStatusService {
  case class ServerStatus(version: String, distribution: String, status: String, namespaces: Long, domains: Long)
}

class ServerStatusService(
  private[this] val executionContext: ExecutionContext,
  private[this] val statusActor:      ActorRef,
  private[this] val defaultTimeout:   Timeout)
  extends JsonSupport
  with Logging {

  import ServerStatusService._
  import akka.pattern.ask

  implicit val ec: ExecutionContext = executionContext
  implicit val t: Timeout = defaultTimeout

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("status") {
      pathEnd {
        get {
          complete(getServerStatus(authProfile))
        }
      }
    }
  }

  def getServerStatus(authProfile: AuthorizationProfile): Future[RestResponse] = {
    val message = GetStatusRequest
    (statusActor ? message).mapTo[ServerStatusResponse].map { status =>
      val ServerStatusResponse(version, distribution, serverStatus, namespaces, domains) = status
      okResponse(ServerStatus(version, distribution, serverStatus, namespaces, domains))
    }
  }
}
