package com.convergencelabs.server.api.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.ServerStatusActor.GetStatusRequest
import com.convergencelabs.server.datastore.convergence.ServerStatusActor.ServerStatusResponse
import com.convergencelabs.server.security.AuthorizationProfile

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.util.Timeout
import grizzled.slf4j.Logging

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

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { authProfile: AuthorizationProfile =>
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
