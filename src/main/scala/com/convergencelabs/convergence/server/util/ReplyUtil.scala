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

package com.convergencelabs.convergence.server.util

import scala.util.Try
import akka.actor.ActorRef
import scala.util.Success
import scala.util.Failure
import akka.actor.Status
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait ReplyUtil {

  def reply[T](result: Try[T], sender: ActorRef): Unit = {
    result match {
      case Success(x) =>
        sender ! x
      case Failure(cause) =>
        sender ! Status.Failure(cause)
    }
  }

  def reply[T](future: Future[T], sender: ActorRef)(implicit ec: ExecutionContext): Unit = {
    future onComplete {
      case Success(s) =>
        sender ! s
      case Failure(cause) =>
        sender ! Status.Failure(cause)
    }
  }
}
