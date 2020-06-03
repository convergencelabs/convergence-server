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

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

package object concurrent {

  private[this] object InternalCallbackExecutor extends ExecutionContext {
    override def execute(r: Runnable): Unit = r.run()

    override def reportFailure(t: Throwable): Unit = throw new IllegalStateException("problem in scala.concurrent internal callback", t)
  }

  private[this] implicit val ec: InternalCallbackExecutor.type = InternalCallbackExecutor

  implicit class AskHandler[E,V](val future: Future[Either[E,V]]) extends AnyVal {
    def handleError(errorMapper: E => Throwable): Future[V] = {
      future.flatMap {
        case Right(v) =>
          Future.successful(v)
        case Left(e) =>
          Future.failed(errorMapper(e))
      }
    }
  }
}
