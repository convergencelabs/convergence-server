package com.convergencelabs.server.util

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.concurrent.Promise
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import scala.language.implicitConversions

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
        case Success(UnexpectedError(code, reason)) => {
          p.failure(new UnexpectedErrorException(code, reason))
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
