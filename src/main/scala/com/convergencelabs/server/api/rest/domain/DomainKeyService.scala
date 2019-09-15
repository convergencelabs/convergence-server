package com.convergencelabs.server.api.rest.domain

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.api.rest.OkResponse
import com.convergencelabs.server.api.rest.RestResponse
import com.convergencelabs.server.api.rest.notFoundResponse
import com.convergencelabs.server.api.rest.okResponse
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStore.KeyInfo
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStoreActor.CreateDomainApiKey
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStoreActor.DeleteDomainApiKey
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStoreActor.GetDomainApiKey
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStoreActor.GetDomainApiKeys
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStoreActor.UpdateDomainApiKey
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.domain.JwtAuthKey
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.security.AuthorizationProfile

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Route
import akka.util.Timeout

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
      case keys: List[JwtAuthKey] => okResponse(keys)
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
