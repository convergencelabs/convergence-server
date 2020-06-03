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

package com.convergencelabs.convergence.server.api.rest.domain


import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.datastore.domain.JwtAuthKeyStore.KeyInfo
import com.convergencelabs.convergence.server.datastore.domain.JwtAuthKeyStoreActor._
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

class DomainKeyService(private[this] val domainRestActor: ActorRef[DomainRestActor.Message],
                       private[this] val system: ActorSystem[_],
                       private[this] val executionContext: ExecutionContext,
                       private[this] val timeout: Timeout)
  extends AbstractDomainRestService(system, executionContext, timeout) {

  import DomainKeyService._

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

  private[this] def getKeys(domain: DomainId): Future[RestResponse] = {
    domainRestActor.ask[GetJwtAuthKeysResponse](r => DomainRestMessage(domain, GetJwtAuthKeysRequest(None, None, r))).flatMap {
      case GetJwtAuthKeysSuccess(keys) =>
        Future.successful(okResponse(keys))
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def getKey(domain: DomainId, keyId: String): Future[RestResponse] = {
    domainRestActor.ask[GetJwtAuthKeyResponse](r => DomainRestMessage(domain, GetJwtAuthKeyRequest(keyId, r))).flatMap {
      case GetJwtAuthKeySuccess(key) =>
        Future.successful(okResponse(key))
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def createKey(domain: DomainId, key: KeyInfo): Future[RestResponse] = {
    domainRestActor.ask[CreateJwtAuthKeyResponse](r =>
      DomainRestMessage(domain, CreateJwtAuthKeyRequest(key, r))).flatMap {
      case RequestSuccess() =>
        Future.successful(CreatedResponse)
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def updateKey(domain: DomainId, keyId: String, update: UpdateInfo): Future[RestResponse] = {
    val UpdateInfo(description, key, enabled) = update
    val info = KeyInfo(keyId, description, key, enabled)
    domainRestActor.ask[UpdateJwtAuthKeyResponse](r =>
      DomainRestMessage(domain, UpdateJwtAuthKeyRequest(info, r))).flatMap {
      case RequestSuccess() =>
        Future.successful(OkResponse)
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def deleteKey(domain: DomainId, keyId: String): Future[RestResponse] = {
    domainRestActor.ask[DeleteJwtAuthKeyResponse](r =>
      DomainRestMessage(domain, DeleteJwtAuthKeyRequest(keyId, r))).flatMap {
      case RequestSuccess() =>
        Future.successful(DeletedResponse)
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }
}

object DomainKeyService {

  case class UpdateInfo(description: String, key: String, enabled: Boolean)

}
