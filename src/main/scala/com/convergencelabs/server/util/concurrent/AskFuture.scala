/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.util

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.UnknownErrorResponse
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException
import com.convergencelabs.server.util.concurrent.UnexpectedResponseException

package object concurrent {

  private[this] object InternalCallbackExecutor extends ExecutionContext {
    override def execute(r: Runnable): Unit = r.run()

    override def reportFailure(t: Throwable): Unit = throw new IllegalStateException("problem in scala.concurrent internal callback", t)
  }

  private[this] implicit val ec = InternalCallbackExecutor

  implicit class AskFuture[T](val future: Future[T]) extends AnyVal {
    def mapResponse[S](implicit tag: ClassTag[S]): Future[S] = {
      val c = tag.runtimeClass

      val p = Promise[S]
      future onComplete {
        case Success(x) if c.isInstance(x) => {
          p.success(c.cast(x).asInstanceOf[S])
        }
        case Success(UnknownErrorResponse(reason)) => {
          p.failure(new UnexpectedErrorException(reason))
        }
        case Success(x) => {
          val message = s"Wanted ${tag.runtimeClass.getName} but got ${x.getClass.getName}"
          p.failure(new UnexpectedResponseException(message))
        }
        case Failure(cause) => {
          p.failure(cause)
        }
      }

      p.future
    }
  }
}
