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

import com.convergencelabs.convergence.server.UnknownErrorResponse

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

package object concurrent {

  private[this] object InternalCallbackExecutor extends ExecutionContext {
    override def execute(r: Runnable): Unit = r.run()

    override def reportFailure(t: Throwable): Unit = throw new IllegalStateException("problem in scala.concurrent internal callback", t)
  }

  private[this] implicit val ec: InternalCallbackExecutor.type = InternalCallbackExecutor

  implicit class AskFuture[T](val future: Future[T]) extends AnyVal {
    def mapResponse[S](implicit tag: ClassTag[S]): Future[S] = {
      val c = tag.runtimeClass

      val p = Promise[S]
      future onComplete {
        case Success(x) if c.isInstance(x) =>
          p.success(c.cast(x).asInstanceOf[S])
        case Success(UnknownErrorResponse(reason)) =>
          p.failure(UnexpectedErrorException(reason))
        case Success(x) =>
          val message = s"Wanted ${tag.runtimeClass.getName} but got ${x.getClass.getName}"
          p.failure(UnexpectedResponseException(message))
        case Failure(cause) =>
          p.failure(cause)
      }

      p.future
    }
  }
}
