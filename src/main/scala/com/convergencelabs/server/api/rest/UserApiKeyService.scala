package com.convergencelabs.server.api.rest

import java.time.Instant

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _segmentStringToPathMatcher, as, complete, concat, delete, entity, get, path, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.datastore.convergence.UserApiKeyStoreActor.{CreateApiKeyRequest, DeleteApiKeyRequest, GetApiKeysForUser, UpdateKeyRequest}
import com.convergencelabs.server.security.AuthorizationProfile
import com.convergencelabs.server.datastore.convergence.UserApiKey

import scala.concurrent.{ExecutionContext, Future}

object UserApiKeyService {

  case class UpdateKeyData(name: String, enabled: Boolean)

  case class CreateKeyData(name: String, enabled: Option[Boolean])

  case class UserApiKeyData(name: String,
                            key: String,
                            enabled: Boolean,
                            lastUsed: Option[Instant])

  case class CreateUserApiKeysResponse(key: UserApiKeyData)

  case class GetUserApiKeysResponse(apiKeys: List[UserApiKeyData])
}

class UserApiKeyService(
                         private[this] val executionContext: ExecutionContext,
                         private[this] val userApiKeyStoreActor: ActorRef,
                         private[this] val defaultTimeout: Timeout) extends JsonSupport {

  import UserApiKeyService._

  implicit val ec: ExecutionContext = executionContext
  implicit val t: Timeout = defaultTimeout

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("apiKeys") {
      concat(
        pathEnd {
          get {
            complete(getApiKeysForUser(authProfile))
          } ~ post {
            entity(as[CreateKeyData]) { keyData =>
              complete(createApiKey(authProfile, keyData))
            }
          }
        },
        path(Segment) { keyId =>
          pathEnd {
            put {
              entity(as[UpdateKeyData]) { keyData =>
                complete(updateApiKey(authProfile, keyId, keyData))
              }
            } ~ delete {
              complete(deleteApiKey(authProfile, keyId))
            }
          }
        }
      )
    }
  }

  def getApiKeysForUser(authProfile: AuthorizationProfile): Future[RestResponse] = {
    val request = GetApiKeysForUser(authProfile.username)
    (userApiKeyStoreActor ? request).mapTo[Set[UserApiKey]] map { keys =>
      val keyData = keys.map(key => {
        val UserApiKey(_, name, keyId, enabled, lastUsed) = key
        UserApiKeyData(name, keyId, enabled, lastUsed)
      }).toList
      val response = GetUserApiKeysResponse(keyData)
      okResponse(response)
    }
  }

  def createApiKey(authProfile: AuthorizationProfile, keyData: CreateKeyData): Future[RestResponse] = {
    val request = CreateApiKeyRequest(authProfile.username, keyData.name, keyData.enabled)
    (userApiKeyStoreActor ? request).mapTo[UserApiKey] map {
      case UserApiKey(_, key, name, enabled, lastUsed) =>
        okResponse(CreateUserApiKeysResponse(UserApiKeyData(key, name, enabled, lastUsed)))
    }
  }

  def updateApiKey(authProfile: AuthorizationProfile, keyId: String, updateData: UpdateKeyData): Future[RestResponse] = {
    val request = UpdateKeyRequest(authProfile.username, keyId, updateData.name, updateData.enabled)
    (userApiKeyStoreActor ? request).mapTo[Unit] map (_ => OkResponse)
  }

  def deleteApiKey(authProfile: AuthorizationProfile, keyId: String): Future[RestResponse] = {
    val request = DeleteApiKeyRequest(authProfile.username, keyId)
    (userApiKeyStoreActor ? request).mapTo[Unit] map (_ => OkResponse)
  }
}
