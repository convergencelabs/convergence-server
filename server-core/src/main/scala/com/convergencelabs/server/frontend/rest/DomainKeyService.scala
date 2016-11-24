package com.convergencelabs.server.frontend.rest

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.JwtAuthKeyStoreActor.CreateDomainApiKey
import com.convergencelabs.server.datastore.JwtAuthKeyStoreActor.DeleteDomainApiKey
import com.convergencelabs.server.datastore.JwtAuthKeyStoreActor.GetDomainApiKey
import com.convergencelabs.server.datastore.JwtAuthKeyStoreActor.GetDomainApiKeys
import com.convergencelabs.server.datastore.JwtAuthKeyStoreActor.UpdateDomainApiKey
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.UpdateSuccess
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStore.KeyInfo
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage
import com.convergencelabs.server.frontend.rest.DomainKeyService.UpdateInfo

import DomainKeyService.GetKeyRestResponse
import DomainKeyService.GetKeysRestResponse
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
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.JwtAuthKey

object DomainKeyService {
  case class GetKeysRestResponse(keys: List[JwtAuthKey]) extends AbstractSuccessResponse
  case class GetKeyRestResponse(key: JwtAuthKey) extends AbstractSuccessResponse
  case class UpdateInfo(description: String, key: String, enabled: Boolean)
}

class DomainKeyService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("keys") {
      pathEnd {
        get {
          complete(getKeys(domain))
        } ~ post {
          entity(as[KeyInfo]) { key =>
            complete(createKey(domain, key))
          }
        }
      } ~ pathPrefix(Segment) { keyId =>
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

  def getKeys(domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, GetDomainApiKeys(None, None))).mapTo[List[JwtAuthKey]] map {
      case keys: List[JwtAuthKey] => (StatusCodes.OK, GetKeysRestResponse(keys))
    }
  }

  def getKey(domain: DomainFqn, keyId: String): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, GetDomainApiKey(keyId))).mapTo[Option[JwtAuthKey]] map {
      case Some(key) => (StatusCodes.OK, GetKeyRestResponse(key))
      case None      => NotFoundError
    }
  }

  def createKey(domain: DomainFqn, key: KeyInfo): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, CreateDomainApiKey(key))).mapTo[CreateResult[Unit]] map {
      case result: CreateSuccess[Unit] => OkResponse
      case DuplicateValue              => DuplicateError
      case InvalidValue                 => InvalidValueError
    }
  }

  def updateKey(domain: DomainFqn, keyId: String, update: UpdateInfo): Future[RestResponse] = {
    val UpdateInfo(description, key, enabled) = update
    val info = KeyInfo(keyId, description, key, enabled)
    (domainRestActor ? DomainMessage(domain, UpdateDomainApiKey(info))).mapTo[UpdateResult] map {
      case UpdateSuccess => OkResponse
      case InvalidValue => InvalidValueError
      case NotFound => NotFoundError
    }
  }

  def deleteKey(domain: DomainFqn, keyId: String): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, DeleteDomainApiKey(keyId))).mapTo[DeleteResult] map {
      case DeleteSuccess => OkResponse
      case NotFound => NotFoundError
    }
  }
}
