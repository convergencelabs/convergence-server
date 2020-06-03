/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.rest

import java.time.Instant

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence.UserApiKey
import com.convergencelabs.convergence.server.datastore.convergence.UserApiKeyStoreActor.{CreateUserApiKeyResponse, _}
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}


private[rest] class CurrentUserApiKeyService(private[this] val userApiKeyStoreActor: ActorRef[Message],
                                             private[this] val system: ActorSystem[_],
                                             private[this] val executionContext: ExecutionContext,
                                             private[this] val defaultTimeout: Timeout)
  extends JsonSupport {

  import CurrentUserApiKeyService._

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout
  private[this] implicit val s: ActorSystem[_] = system

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("user" / "apiKeys") {
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
            get {
              complete(getApiKey(authProfile, keyId))
            } ~
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

  private[this] def getApiKeysForUser(authProfile: AuthorizationProfile): Future[RestResponse] = {
    userApiKeyStoreActor.ask[GetApiKeysForUserResponse](GetApiKeysForUserRequest(authProfile.username, _)).map {
      case GetApiKeysForUserSuccess(keys) =>
        val keyData = keys.map(key => {
          val UserApiKey(_, name, keyId, enabled, lastUsed) = key
          UserApiKeyData(name, keyId, enabled, lastUsed)
        }).toList
        okResponse(keyData)
      case _ =>
        InternalServerError
    }
  }

  private[this] def getApiKey(authProfile: AuthorizationProfile, key: String): Future[RestResponse] = {
    userApiKeyStoreActor.ask[GetUserApiKeyResponse](GetUserApiKeyRequest(authProfile.username, key, _)).map {
      case GetUserApiKeySuccess(Some(UserApiKey(_, name, keyId, enabled, lastUsed))) =>
        okResponse(UserApiKeyData(name, keyId, enabled, lastUsed))
      case GetUserApiKeySuccess(None) =>
        notFoundResponse(Some(s"A key with id '$key' could not be found"))
      case _ =>
        InternalServerError
    }
  }

  private[this] def createApiKey(authProfile: AuthorizationProfile, keyData: CreateKeyData): Future[RestResponse] = {
    userApiKeyStoreActor.ask[CreateUserApiKeyResponse](CreateUserApiKeyRequest(authProfile.username, keyData.name, keyData.enabled, _)).flatMap {
      case RequestSuccess() =>
        Future.successful(CreatedResponse)
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def updateApiKey(authProfile: AuthorizationProfile, keyId: String, updateData: UpdateKeyData): Future[RestResponse] = {
    userApiKeyStoreActor.ask[UpdateUserApiKeyResponse](UpdateUserApiKeyRequest(authProfile.username, keyId, updateData.name, updateData.enabled, _)).flatMap {
      case RequestSuccess() =>
        Future.successful(CreatedResponse)
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def deleteApiKey(authProfile: AuthorizationProfile, keyId: String): Future[RestResponse] = {
    userApiKeyStoreActor.ask[DeleteUserApiKeyResponse](DeleteUserApiKeyRequest(authProfile.username, keyId, _)).flatMap {
      case RequestSuccess() =>
        Future.successful(DeletedResponse)
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }
}

object CurrentUserApiKeyService {

  case class UpdateKeyData(name: String, enabled: Boolean)

  case class CreateKeyData(name: String, enabled: Option[Boolean])

  case class UserApiKeyData(name: String,
                            key: String,
                            enabled: Boolean,
                            lastUsed: Option[Instant])

}
