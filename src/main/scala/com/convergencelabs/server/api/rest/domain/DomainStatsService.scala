package com.convergencelabs.server.api.rest.domain

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives.{_segmentStringToPathMatcher, complete, get, path}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.api.rest.{RestResponse, okResponse}
import com.convergencelabs.server.datastore.domain.DomainStatsActor.{DomainStats, GetStats}
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

object DomainStatsService {
  case class DomainStatsRestData(activeSessionCount: Long, userCount: Long, modelCount: Long, dbSize: Long)
}

class DomainStatsService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val domainRestActor: ActorRef)
  extends DomainRestService(executionContext, timeout) {

  import DomainStatsService._
  import akka.pattern.ask
  
  def route(authProfile: AuthorizationProfile, domain: DomainId): Route =
    (path("stats") & get) {
      complete(getStats(domain))
    }

  def getStats(domain: DomainId): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, GetStats))
    .mapTo[DomainStats]
    .map { stats => 
      val DomainStats(activeSessionCount, userCount, modelCount, dbSize) = stats
      okResponse(DomainStatsRestData(activeSessionCount, userCount, modelCount, dbSize)) 
    
    }
  }
}
