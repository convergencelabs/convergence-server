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

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _segmentStringToPathMatcher, as, complete, delete, entity, get, path, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.datastore.domain.JwtAuthKeyStore.KeyInfo
import com.convergencelabs.convergence.server.datastore.domain.JwtAuthKeyStoreActor._
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

object DomainKeyService {

  case class UpdateInfo(description: String, key: String, enabled: Boolean)

}

class DomainKeyService(private[this] val executionContext: ExecutionContext,
                       private[this] val timeout: Timeout,
                       private[this] val domainRestActor: ActorRef)
  extends DomainRestService(executionContext, timeout) {

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
    (domainRestActor ? DomainRestMessage(domain, GetJwtAuthKeysRequest(None, None))).mapTo[GetJwtAuthKeysResponse] map {
      response => okResponse(response.keys)
    }
  }

  private[this] def getKey(domain: DomainId, keyId: String): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, GetJwtAuthKeyRequest(keyId))).mapTo[GetJwtAuthKeyResponse] map { response =>
      response.key match {
        case Some(key) => okResponse(key)
        case None => notFoundResponse()
      }
    }
  }

  private[this] def createKey(domain: DomainId, key: KeyInfo): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, CreateDomainJwtAuthKey(key))) map (_ => CreatedResponse)
  }

  private[this] def updateKey(domain: DomainId, keyId: String, update: UpdateInfo): Future[RestResponse] = {
    val UpdateInfo(description, key, enabled) = update
    val info = KeyInfo(keyId, description, key, enabled)
    (domainRestActor ? DomainRestMessage(domain, UpdateDomainJwtAuthKey(info))) map (_ => OkResponse)
  }

  private[this] def deleteKey(domain: DomainId, keyId: String): Future[RestResponse] = {
    (domainRestActor ? DomainRestMessage(domain, DeleteDomainJwtAuthKey(keyId))) map (_ => DeletedResponse)
  }
}
