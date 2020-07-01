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

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.BuildInfo

import scala.concurrent.{ExecutionContext, Future}

object InfoService {

  case class InfoResponse(version: String)

  val InfoRestResponse: (StatusCode, RestResponseEntity) = okResponse(InfoResponse(BuildInfo.version))
}

class InfoService(executionContext: ExecutionContext, defaultTimeout: Timeout) extends JsonSupport {

  import InfoService._

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout

  val route: Route = get {
    path("") {
      complete(Future.successful(InfoRestResponse))
    } ~ path("health") {
      complete(Future.successful(OkResponse))
    }
  }
}
