package com.convergencelabs.server.api.rest.domain

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{_enhanceRouteWithConcatenation, _segmentStringToPathMatcher, as, complete, delete, entity, get, path, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.api.rest.{OkResponse, RestResponse, notFoundResponse, okResponse}
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStore.KeyInfo
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStoreActor._
import com.convergencelabs.server.domain.{DomainId, JwtAuthKey}
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

object DomainKeyService {
  case class UpdateInfo(description: String, key: String, enabled: Boolean)
}

class DomainKeyService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val domainRestActor: ActorRef)
  extends DomainRestService(executionContext, timeout) {

  import DomainKeyService._
  import akka.http.scaladsl.server.Directives.Segment
  import akka.pattern.ask

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("jwtKeys") {
      pathEnd {
        get {
          complete(getKeys(domain))
        } ~ post {
          entity(as[KeyInfo]) { key =>
            complete(createKey(domain, key))
          }
        }
      } ~ path(Segment) { keyId =>
        get {
          complete(getKey(domain, keyId))
        } ~ put {
          entity(as[UpdateInfo]) { key =>
            complete(updateKey(domain, keyId, key))
          }
        } ~ delete {
          complete(deleteKey(domain, keyId))
        }
      }
    }
  }

  def getKeys(domain: DomainId): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, GetDomainApiKeys(None, None))).mapTo[List[JwtAuthKey]] map {
      keys => okResponse(keys)
    }
  }

  def getKey(domain: DomainId, keyId: String): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, GetDomainApiKey(keyId))).mapTo[Option[JwtAuthKey]] map {
      case Some(key) => okResponse(key)
      case None => notFoundResponse()
    }
  }

  def createKey(domain: DomainId, key: KeyInfo): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, CreateDomainApiKey(key))) map { _ =>
      OkResponse
    }
  }

  def updateKey(domain: DomainId, keyId: String, update: UpdateInfo): Future[RestResponse] = {
    val UpdateInfo(description, key, enabled) = update
    val info = KeyInfo(keyId, description, key, enabled)
    (domainRestActor ? DomainRestMessage(domain, UpdateDomainApiKey(info))) map { _ =>
      OkResponse
    }
  }

  def deleteKey(domain: DomainId, keyId: String): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, DeleteDomainApiKey(keyId))) map { _ =>
      OkResponse
    }
  }
}
