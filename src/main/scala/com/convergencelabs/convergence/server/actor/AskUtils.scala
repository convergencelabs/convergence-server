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

package com.convergencelabs.convergence.server.actor

import java.util.concurrent.TimeoutException

import com.convergencelabs.convergence.server.api.realtime.ReplyCallback
import grizzled.slf4j.Logging

import scala.concurrent.Future

trait AskUtils extends Logging {
   def handleAskFailure[T](cause: Throwable, cb: ReplyCallback): Future[T] = {
    cause match {
      case cause: TimeoutException =>
        val message = "an internal timeout occurred"
        error(message, cause)
        cb.unexpectedError(message)
      case cause =>
        val message = "an unexpected error occurred"
        error(message, cause)
    }

    Future.failed(cause)
  }
}
