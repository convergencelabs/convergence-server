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

import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.convergencelabs.convergence.server.api.rest.KeyGenService.CreateTokenResponse
import com.convergencelabs.convergence.server.backend.services.domain.JwtUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

private[rest] object KeyGenService {

  case class CreateTokenResponse(publicKey: String, privateKey: String)

}

private[rest] class KeyGenService(executionContext: ExecutionContext) extends JsonSupport {

  private[this] implicit val ec: ExecutionContext = executionContext

  def route(): Route = {
    pathPrefix("util" / "keygen") {
      pathEnd {
        get {
          complete(createKey())
        }
      }
    }
  }

  private[this] def createKey(): Future[RestResponse] = {
    Future {
      for {
        rsaJsonWebKey <- JwtUtil.createKey()
        publicKey <- JwtUtil.getPublicCertificatePEM(rsaJsonWebKey)
        privateKey <- JwtUtil.getPrivateKeyPEM(rsaJsonWebKey)
      } yield {
        okResponse(CreateTokenResponse(publicKey, privateKey))
      }
    }.flatMap {
      case Success(s) => Future.successful(s)
      case Failure(fail) => Future.failed(fail)
    }
  }
}
